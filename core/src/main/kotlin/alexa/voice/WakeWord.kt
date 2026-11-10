package alexa.voice

/**
 * Logika wake worda bez mikrofona: detektor daje rezultat, a ova klasa odlučuje
 * da li se Alexa probudi. Stvarni detektor (lokalni model, Android) je zamjenjiv.
 *
 * Pravila: prag povjerenja i kratka pauza nakon buđenja, da jedna riječ ne
 * probudi Alexu više puta.
 */
class WakeWordGate(
    private val threshold: Double = 0.85,
    private val cooldownMs: Long = 2_000,
) {
    private var lastTriggerMs: Long? = null

    /** Vraća true ako se Alexa treba probuditi za ovu detekciju. */
    fun onDetection(score: Double, nowMs: Long): Boolean {
        if (score < threshold) return false
        val last = lastTriggerMs
        if (last != null && nowMs - last < cooldownMs) return false
        lastTriggerMs = nowMs
        return true
    }
}
