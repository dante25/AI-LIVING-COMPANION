package io.androllm.core.voice.tts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.androllm.core.voice.VoiceSettingsStore
import io.androllm.core.voice.audio.AudioPlayer
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Čita tekst naglas rečenicu po rečenicu (sinteza + reprodukcija u pozadini),
 * za obični chat. Koristi izabrani motor govora ([OfflineTtsEngine]) i
 * [AudioPlayer]; glasovni asistent ostaje odvojena funkcija.
 */
@Singleton
class ReadAloudSpeaker @Inject constructor(
    @ApplicationContext context: Context,
    private val engine: OfflineTtsEngine,
    private val settingsStore: VoiceSettingsStore
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val player = AudioPlayer(context)
    private val generation = AtomicInteger(0)
    private val lock = Any()
    private var job: Job? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    /** Zaustavlja eventualno trenutno čitanje i počinje čitati [text]. */
    fun speak(text: String) {
        val sentences = SpeechTextSplitter.splitSentences(text)
        synchronized(lock) {
            val myGen = generation.incrementAndGet()
            val previous = job
            previous?.cancel()
            player.stopNow()
            if (sentences.isEmpty()) {
                _isSpeaking.value = false
                return
            }
            _isSpeaking.value = true
            job = scope.launch {
                try {
                    // Sačekaj da se prethodni posao potpuno zaustavi (AudioPlayer
                    // dozvoljava samo jednu reprodukciju istovremeno).
                    previous?.cancelAndJoin()
                    currentCoroutineContext().ensureActive()
                    runSentences(sentences)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "ReadAloudSpeaker: čitanje nije uspjelo")
                } finally {
                    if (generation.get() == myGen) _isSpeaking.value = false
                }
            }
        }
    }

    /** Odmah prekida čitanje. */
    fun stop() {
        synchronized(lock) {
            generation.incrementAndGet()
            job?.cancel()
            player.stopNow()
            _isSpeaking.value = false
        }
    }

    private suspend fun runSentences(sentences: List<String>) {
        val speed = runCatching { settingsStore.current().speakingSpeed }.getOrDefault(1.0f)
        if (!engine.ensureInitialized()) {
            Timber.w("ReadAloudSpeaker: motor govora nije spreman")
            return
        }
        for (sentence in sentences) {
            currentCoroutineContext().ensureActive()
            val samples = engine.synthesize(sentence, speed) ?: continue
            currentCoroutineContext().ensureActive()
            val rate = engine.sampleRate
            val finished = player.play(samples, rate)
            if (!finished) return
        }
    }
}
