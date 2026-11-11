package io.androllm.feature.chat.voice

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.androllm.core.permissions.SessionManager
import io.androllm.core.tools.api.ToolPermission
import io.androllm.core.tools.policy.PermissionPolicy
import io.androllm.core.tools.settings.AutomationSettingsStore
import io.androllm.core.voice.VoiceSettingsStore
import io.androllm.core.voice.asr.SpeechRecognizer
import io.androllm.core.voice.audio.AudioRecorder
import io.androllm.core.voice.model.VoiceModels
import io.androllm.core.voice.stt.SttDefaults
import io.androllm.core.voice.stt.WhisperModel
import io.androllm.core.voice.stt.WhisperModelManager
import io.androllm.core.voice.stt.WhisperModels
import io.androllm.core.voice.tts.ReadAloudSpeaker
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface VoiceInputState {
    data object Idle : VoiceInputState
    data class Recording(val elapsedMs: Long, val level: Float) : VoiceInputState
    data object Loading : VoiceInputState
    data object Transcribing : VoiceInputState
    data class NeedsModel(val sizeMb: Int) : VoiceInputState
    data class Downloading(val progress: Float) : VoiceInputState
    data class Error(val message: String) : VoiceInputState
}

/** Recognized speech; [autoSend] mirrors the "Automatski posalji govor" setting. */
data class VoiceTranscript(val text: String, val autoSend: Boolean)

/**
 * Push-to-talk speech input for the chat: AudioRecorder -> SpeechRecognizer
 * (whisper), same pipeline as the voice assistant service. Owns its own scope;
 * call [release] when the owner goes away.
 */
private const val TAG = "ChatVoiceInput"

class ChatVoiceInput @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recognizer: SpeechRecognizer,
    private val modelManager: WhisperModelManager,
    private val voiceSettingsStore: VoiceSettingsStore,
    private val automationSettingsStore: AutomationSettingsStore,
    private val sessionManager: SessionManager,
    private val readAloudSpeaker: ReadAloudSpeaker
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    @Volatile private var recorder: AudioRecorder? = null

    private val _state = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    private val _transcripts = MutableSharedFlow<VoiceTranscript>(extraBufferCapacity = 4)
    val transcripts: SharedFlow<VoiceTranscript> = _transcripts.asSharedFlow()

    /** True when the user turned the microphone OFF in the permission policies. */
    suspend fun isMicBlockedByPolicy(): Boolean =
        runCatching {
            automationSettingsStore.current().policyFor(ToolPermission.MICROPHONE) == PermissionPolicy.OFF
        }.getOrDefault(false)

    /** Android RECORD_AUDIO is granted: counts as the user's confirmation for the session. */
    fun onMicPermissionGranted() {
        sessionManager.touch()
        sessionManager.grant(ToolPermission.MICROPHONE)
    }

    /** Starts push-to-talk (no-op while a turn is already running). */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                readAloudSpeaker.stop()
                val settings = voiceSettingsStore.current()
                _state.value = VoiceInputState.Loading
                val ready = runCatching { recognizer.ensureInitialized() }
                    .onFailure { android.util.Log.e(TAG, "ensureInitialized failed", it) }
                    .getOrDefault(false)
                if (!ready) {
                    _state.value = VoiceInputState.NeedsModel(defaultModel().let { (it.sizeBytes / (1024 * 1024)).toInt() })
                    return@launch
                }
                recordAndTranscribe(settings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = VoiceInputState.Error("Govor trenutno ne radi")
            }
        }
    }

    /** User confirmed the model download dialog. */
    fun confirmDownload() {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                val model = defaultModel()
                _state.value = VoiceInputState.Downloading(0f)
                modelManager.download(model) { done, total ->
                    val p = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
                    _state.value = VoiceInputState.Downloading(p)
                }
                voiceSettingsStore.update { it.copy(whisperModel = model.id) }
                val ready = runCatching { recognizer.ensureInitialized() }.getOrDefault(false)
                if (!ready) {
                    _state.value = VoiceInputState.Error("Model nije mogao biti učitan")
                    return@launch
                }
                recordAndTranscribe(voiceSettingsStore.current())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = VoiceInputState.Error("Preuzimanje nije uspjelo")
            }
        }
    }

    /** User tapped the stop icon: finish recording and transcribe. */
    fun stopRecording() {
        if (_state.value is VoiceInputState.Recording) recorder?.stop()
    }

    /** Dismisses the model dialog or an error and returns to idle. */
    fun dismiss() {
        if (job?.isActive != true) _state.value = VoiceInputState.Idle
    }

    /** Aborts whatever is running (leaving the screen) without producing text. */
    fun cancel() {
        job?.cancel()
        job = null
        runCatching { recorder?.release() }
        recorder = null
        _state.value = VoiceInputState.Idle
    }

    /** Releases the recorder and the scope; the instance is unusable afterwards. */
    fun release() {
        cancel()
        scope.cancel()
    }

    private fun defaultModel(): WhisperModel {
        val ramGb = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            Math.round(info.totalMem / (1024.0 * 1024.0 * 1024.0))
        }.getOrDefault(4L)
        return WhisperModels.byId(SttDefaults.defaultWhisperModelId(ramGb))
            ?: WhisperModels.byId(SttDefaults.FALLBACK_MODEL_ID)!!
    }

    private suspend fun recordAndTranscribe(settings: io.androllm.core.voice.model.VoiceSettings) {
        val rec = AudioRecorder(
            noiseSuppression = settings.noiseSuppression,
            echoCancellation = settings.echoCancellation
        )
        recorder = rec
        val started = runCatching { rec.start() }.getOrDefault(false)
        if (!started) {
            recorder = null
            _state.value = VoiceInputState.Error("Mikrofon nije dostupan")
            return
        }
        val session = try {
            recognizer.startSession(
                language = settings.sttLanguage,
                translate = settings.sttTranslate,
                numThreads = settings.sttThreads,
                beamSize = settings.sttBeamSize,
                temperature = settings.sttTemperature,
                maxSeconds = (SilenceDetector.MAX_RECORDING_MS / 1000L).toInt(),
                streamingEnabled = false
            )
        } catch (e: CancellationException) {
            rec.release(); recorder = null
            throw e
        } catch (e: Exception) {
            rec.release(); recorder = null
            _state.value = VoiceInputState.Error("Prepoznavanje govora nije dostupno")
            return
        }

        var detector = SilenceDetector.State()
        try {
            _state.value = VoiceInputState.Recording(0L, 0f)
            for (chunk in rec.chunks) {
                session.append(chunk)
                val rms = SilenceDetector.rms(chunk)
                val chunkMs = chunk.size * 1000L / VoiceModels.SAMPLE_RATE
                detector = SilenceDetector.advance(detector, rms, chunkMs)
                _state.value = VoiceInputState.Recording(
                    detector.elapsedMs,
                    (rms * 8f).coerceIn(0f, 1f)
                )
                if (SilenceDetector.stopReason(detector) != null) break
            }
            rec.release()
            recorder = null

            android.util.Log.i(TAG, "recording ended: ${detector.elapsedMs}ms speech=${detector.speechDetected}")
            if (!detector.speechDetected) {
                _state.value = VoiceInputState.Error("Ne čujem govor. Provjeri mikrofon i govori glasnije")
                return
            }
            _state.value = VoiceInputState.Transcribing
            val text = withContext(Dispatchers.Default) {
                runCatching { session.finish().text.trim() }
                    .onFailure { android.util.Log.e(TAG, "transcription failed", it) }
                    .getOrDefault("")
            }
            if (text.isBlank()) {
                _state.value = VoiceInputState.Error("Nisam te čula, probaj ponovo")
            } else {
                _transcripts.tryEmit(VoiceTranscript(text, settings.autoSendSpeech))
                _state.value = VoiceInputState.Idle
            }
        } finally {
            runCatching { rec.release() }
            recorder = null
            runCatching { session.release() }
        }
    }
}
