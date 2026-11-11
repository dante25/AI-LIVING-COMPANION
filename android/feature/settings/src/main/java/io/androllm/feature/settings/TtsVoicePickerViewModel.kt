package io.androllm.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.androllm.core.voice.audio.AudioPlayer
import io.androllm.core.voice.tts.AndroidSystemTtsEngine
import io.androllm.core.voice.tts.GeminiOfflineTtsEngine
import io.androllm.core.voice.tts.PiperSpeechSynthesizer
import io.androllm.core.voice.tts.TtsEngineIds
import io.androllm.core.voice.tts.TtsVoiceInfo
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Stanje i radnje za izbor glasa Alexe: lista sistemskih glasova i preslušavanje. */
@HiltViewModel
class TtsVoicePickerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val system: AndroidSystemTtsEngine,
    private val piper: PiperSpeechSynthesizer,
    private val gemini: GeminiOfflineTtsEngine
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val voices: List<TtsVoiceInfo> = emptyList(),
        /** Naziv glasa koji se trenutno preslušava ("" = automatski izbor, null = ništa). */
        val previewing: String? = null,
        val message: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val player = AudioPlayer(context)
    private var loadJob: Job? = null
    private var previewJob: Job? = null

    fun loadVoices() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(loading = true, message = null) }
            val voices = runCatching { system.listVoices() }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    loading = false,
                    voices = voices,
                    message = if (voices.isEmpty()) {
                        "Nema dostupnih sistemskih glasova. Instaliraj glasove ili provjeri TTS motor u postavkama telefona."
                    } else {
                        null
                    }
                )
            }
        }
    }

    /**
     * Izgovara primjer. Za sistemski motor koristi [voiceName] ("" = automatski izbor),
     * a za ostale motore njihov podrazumijevani glas.
     */
    fun preview(engineId: String, voiceName: String, speed: Float) {
        stopPreview()
        previewJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(previewing = voiceName, message = null) }
            try {
                val engine = TtsEngineIds.normalize(engineId)
                val samples: FloatArray?
                val rate: Int
                when (engine) {
                    TtsEngineIds.PIPER -> {
                        samples = if (piper.ensureInitialized()) piper.synthesize(PREVIEW_TEXT, speed) else null
                        rate = piper.sampleRate
                    }
                    TtsEngineIds.GEMINI -> {
                        samples = gemini.synthesize(PREVIEW_TEXT, speed)
                        rate = gemini.sampleRate
                    }
                    else -> {
                        samples = system.synthesizeWithVoice(PREVIEW_TEXT, speed, voiceName)
                        rate = system.sampleRate
                    }
                }
                if (samples == null || samples.isEmpty()) {
                    _state.update { it.copy(message = "Preslušavanje nije uspjelo. Provjeri da li je glas instaliran.") }
                } else {
                    player.play(samples, rate)
                }
            } finally {
                _state.update { it.copy(previewing = null) }
            }
        }
    }

    fun stopPreview() {
        previewJob?.cancel()
        player.stopNow()
    }

    override fun onCleared() {
        stopPreview()
        super.onCleared()
    }

    companion object {
        const val PREVIEW_TEXT = "Zdravo, ja sam Alexa. Drago mi je da te upoznajem."
    }
}
