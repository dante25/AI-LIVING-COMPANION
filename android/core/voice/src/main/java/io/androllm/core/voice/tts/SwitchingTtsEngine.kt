package io.androllm.core.voice.tts

import io.androllm.core.voice.VoiceSettingsStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * [OfflineTtsEngine] koji u trenutku sinteze delegira na motor izabran u
 * postavkama (VoiceSettings.ttsEngine), pa promjena važi odmah, bez
 * ponovnog pokretanja servisa.
 */
@Singleton
class SwitchingTtsEngine @Inject constructor(
    private val settingsStore: VoiceSettingsStore,
    private val system: AndroidSystemTtsEngine,
    private val piper: PiperSpeechSynthesizer,
    private val gemini: GeminiOfflineTtsEngine
) : OfflineTtsEngine {

    @Volatile private var active: OfflineTtsEngine = system

    private fun select(): OfflineTtsEngine {
        val id = runCatching { runBlocking { settingsStore.current().ttsEngine } }
            .getOrDefault(TtsEngineIds.SYSTEM)
        return when (TtsEngineIds.normalize(id)) {
            TtsEngineIds.PIPER -> piper
            TtsEngineIds.GEMINI -> gemini
            else -> system
        }
    }

    override val isInitialized: Boolean
        get() = active.isInitialized

    override val sampleRate: Int
        get() = active.sampleRate

    override fun ensureInitialized(): Boolean {
        val engine = select()
        active = engine
        return engine.ensureInitialized()
    }

    override fun synthesize(text: String, speed: Float): FloatArray? {
        val engine = select()
        if (engine !== active) {
            // Korisnik je promijenio motor usred razgovora.
            active = engine
            if (!engine.isInitialized && !engine.ensureInitialized()) return null
        }
        return engine.synthesize(text, speed)
    }

    override fun release() {
        listOf(system, piper, gemini).forEach { e ->
            runCatching { e.release() }.onFailure { Timber.w(it, "TTS release nije uspio") }
        }
    }
}
