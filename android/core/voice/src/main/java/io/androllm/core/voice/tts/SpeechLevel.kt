package io.androllm.core.voice.tts

import kotlin.math.sqrt

/**
 * Čiste funkcije za nivo glasnoće govora (0..1), koje koristi lik na ekranu
 * da otvara usta. Nema Androida pa se lako testira.
 */
object SpeechLevel {

    /** Dužina prozora uzoraka u milisekundama. */
    const val WINDOW_MS = 30

    /** Pojačanje: govor je obično RMS 0.05–0.2, a nivo treba popuniti raspon 0..1. */
    private const val GAIN = 5f

    /** RMS vrijednost uzoraka [from, to) pretvorena u nivo 0..1. */
    fun level(samples: FloatArray, from: Int = 0, to: Int = samples.size): Float {
        val start = from.coerceAtLeast(0)
        val end = to.coerceAtMost(samples.size)
        if (end <= start) return 0f
        var sum = 0.0
        for (i in start until end) {
            val v = samples[i].toDouble()
            sum += v * v
        }
        val rms = sqrt(sum / (end - start)).toFloat()
        return (rms * GAIN).coerceIn(0f, 1f)
    }

    /** Nivo po prozoru od [windowMs] milisekundi; zadnji prozor može biti kraći. */
    fun windowLevels(samples: FloatArray, sampleRate: Int, windowMs: Int = WINDOW_MS): FloatArray {
        if (samples.isEmpty() || sampleRate <= 0) return FloatArray(0)
        val windowSize = (sampleRate.toLong() * windowMs / 1000L).toInt().coerceAtLeast(1)
        val count = (samples.size + windowSize - 1) / windowSize
        return FloatArray(count) { w -> level(samples, w * windowSize, (w + 1) * windowSize) }
    }

    /** Nivo koji odgovara trenutku [elapsedMs] od početka reprodukcije (0 izvan zvuka). */
    fun levelAt(levels: FloatArray, elapsedMs: Long, windowMs: Int = WINDOW_MS): Float {
        if (elapsedMs < 0) return 0f
        val index = (elapsedMs / windowMs).toInt()
        return if (index in levels.indices) levels[index] else 0f
    }
}
