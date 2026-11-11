package io.androllm.core.voice.tts

import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import dagger.hilt.android.qualifiers.ApplicationContext
import io.androllm.core.voice.VoiceSettingsStore
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * Sinteza govora preko sistemskog Android TextToSpeech motora.
 *
 * Svaka rečenica se renderuje u privremeni WAV fajl (synthesizeToFile), iz
 * kojeg se čita PCM 16-bit i vraća kao float -1..1, pa servis reprodukuje
 * zvuk istim putem kao i ostali motori.
 *
 * Pozivi su blokirajući i namijenjeni pozadinskim nitima. Na glavnoj niti
 * [ensureInitialized] i [synthesize] odmah vraćaju false/null jer bi čekanje
 * na povratni poziv motora (koji stiže na glavnoj niti) zaglavilo aplikaciju.
 */
@Singleton
class AndroidSystemTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: VoiceSettingsStore
) : OfflineTtsEngine {

    private class Waiter {
        val latch = CountDownLatch(1)
        @Volatile var failed = false
    }

    private val lock = Any()
    private val pending = ConcurrentHashMap<String, Waiter>()

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var lastSampleRate: Int = DEFAULT_SAMPLE_RATE
    private var appliedVoiceName: String? = null

    override val isInitialized: Boolean
        get() = tts != null

    override val sampleRate: Int
        get() = lastSampleRate

    override fun ensureInitialized(): Boolean {
        if (tts != null) return true
        if (onMainThread()) {
            Timber.w("SystemTTS: ensureInitialized pozvan na glavnoj niti — preskačem")
            return false
        }
        return synchronized(lock) { initLocked() }
    }

    private fun initLocked(): Boolean {
        if (tts != null) return true
        val latch = CountDownLatch(1)
            val status = AtomicInteger(TextToSpeech.ERROR)
            val engine = try {
                TextToSpeech(context) { s ->
                    status.set(s)
                    latch.countDown()
                }
            } catch (e: Exception) {
                Timber.w(e, "SystemTTS: nije moguće kreirati TextToSpeech")
                return false
            }
            val ok = try {
                latch.await(INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS) && status.get() == TextToSpeech.SUCCESS
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
            if (!ok) {
                Timber.w("SystemTTS: motor nije dostupan (status=%d)", status.get())
                runCatching { engine.shutdown() }
                return false
            }
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    pending[utteranceId ?: return]?.latch?.countDown()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    fail(utteranceId)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    fail(utteranceId)
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    fail(utteranceId)
                }

                private fun fail(utteranceId: String?) {
                    val w = pending[utteranceId ?: return] ?: return
                    w.failed = true
                    w.latch.countDown()
                }
            })
            appliedVoiceName = null
            tts = engine
            return true
    }

    override fun synthesize(text: String, speed: Float): FloatArray? {
        val settings = runCatching { runBlocking { settingsStore.current() } }.getOrNull()
        return render(text, speed, settings?.systemVoiceName.orEmpty(), settings?.pitch ?: 1f)
    }

    /** Sinteza sa tačno određenim glasom (preslušavanje u postavkama). */
    fun synthesizeWithVoice(text: String, speed: Float, voiceName: String): FloatArray? =
        render(text, speed, voiceName, 1f)

    /**
     * Instalirani glasovi sistemskog motora, sortirani (hr, sr, bs, en, ostalo).
     * Prazna lista kada motor nije dostupan. Blokira — samo za pozadinske niti.
     */
    fun listVoices(): List<TtsVoiceInfo> {
        if (!ensureInitialized()) return emptyList()
        val engine = tts ?: return emptyList()
        return TtsVoiceRanking.sort(readVoices(engine).map { it.first })
    }

    private fun render(text: String, speed: Float, voiceName: String, pitch: Float): FloatArray? {
        if (text.isBlank()) return null
        if (onMainThread()) {
            Timber.w("SystemTTS: synthesize pozvan na glavnoj niti — preskačem")
            return null
        }
        if (!ensureInitialized()) return null
        return synchronized(lock) { renderLocked(text, speed, voiceName, pitch) }
    }

    private fun renderLocked(text: String, speed: Float, voiceName: String, pitch: Float): FloatArray? {
            val engine = tts ?: return null
            applyVoice(engine, voiceName)
            engine.setSpeechRate(speed.coerceIn(0.5f, 2.0f))
            engine.setPitch(pitch.coerceIn(0.5f, 2.0f))

            val input = text.take(maxInputLength())
            val file = try {
                File.createTempFile("tts_", ".wav", context.cacheDir)
            } catch (e: Exception) {
                Timber.w(e, "SystemTTS: nije moguće kreirati privremeni fajl")
                return null
            }
            val id = UUID.randomUUID().toString()
            val waiter = Waiter()
            pending[id] = waiter
            return try {
                val rc = engine.synthesizeToFile(input, Bundle(), file, id)
                if (rc != TextToSpeech.SUCCESS) {
                    Timber.w("SystemTTS: synthesizeToFile odbijen (%d)", rc)
                    return null
                }
                val timeout = BASE_TIMEOUT_MS + input.length * PER_CHAR_TIMEOUT_MS
                if (!waiter.latch.await(timeout, TimeUnit.MILLISECONDS)) {
                    Timber.w("SystemTTS: isteklo vrijeme sinteze")
                    runCatching { engine.stop() }
                    return null
                }
                if (waiter.failed) return null
                val decoded = WavPcm.parse(file.readBytes()) ?: run {
                    Timber.w("SystemTTS: WAV zaglavlje nije valjano")
                    return null
                }
                if (decoded.samples.isEmpty()) return null
                lastSampleRate = decoded.sampleRate
                decoded.samples
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                null
            } catch (e: Exception) {
                Timber.w(e, "SystemTTS: sinteza nije uspjela")
                null
            } finally {
                pending.remove(id)
                file.delete()
            }
    }

    private fun applyVoice(engine: TextToSpeech, requested: String) {
        val voices = readVoices(engine)
        if (voices.isEmpty()) return
        val chosen = TtsVoiceRanking.pickBest(voices.map { it.first }, requested) ?: return
        if (chosen.name == appliedVoiceName) return
        val target = voices.firstOrNull { it.first.name == chosen.name }?.second ?: return
        val rc = runCatching { engine.setVoice(target) }.getOrDefault(TextToSpeech.ERROR)
        if (rc == TextToSpeech.SUCCESS) appliedVoiceName = chosen.name
    }

    /** Parovi (opis, Voice) samo za instalirane glasove. */
    private fun readVoices(engine: TextToSpeech): List<Pair<TtsVoiceInfo, Voice>> {
        val raw: Set<Voice> = try {
            engine.voices ?: emptySet()
        } catch (e: Exception) {
            Timber.w(e, "SystemTTS: getVoices nije uspjelo")
            emptySet()
        }
        return raw
            .filter { !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .map { v ->
                TtsVoiceInfo(
                    name = v.name,
                    languageTag = v.locale.toLanguageTag(),
                    language = TtsVoiceRanking.normalizeLanguage(v.locale.language),
                    networkRequired = v.isNetworkConnectionRequired,
                    quality = v.quality
                ) to v
            }
    }

    private fun maxInputLength(): Int =
        runCatching { TextToSpeech.getMaxSpeechInputLength() }.getOrDefault(4000).coerceAtLeast(1)

    private fun onMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    override fun release() {
        val engine = tts
        tts = null
        appliedVoiceName = null
        pending.values.forEach {
            it.failed = true
            it.latch.countDown()
        }
        if (engine != null) {
            runCatching { engine.stop() }
            runCatching { engine.shutdown() }
        }
    }

    private companion object {
        const val DEFAULT_SAMPLE_RATE = 22_050
        const val INIT_TIMEOUT_MS = 8_000L
        const val BASE_TIMEOUT_MS = 15_000L
        const val PER_CHAR_TIMEOUT_MS = 40L
    }
}
