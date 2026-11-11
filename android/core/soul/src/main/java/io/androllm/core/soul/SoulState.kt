package io.androllm.core.soul

/**
 * Brojčano stanje Alexine duše (0–100). Ponašanje odlučuje pravilo u [decide],
 * a model samo glumi dobijeno stanje, pa je karakter dosljedan, a ne nasumičan.
 */
const val MOOD_HOME = 60.0

private fun clamp(v: Double): Double = v.coerceIn(0.0, 100.0)

data class SoulState(
    val mood: Double = 60.0,
    val energy: Double = 80.0,
    val boredom: Double = 10.0,
    val anger: Double = 0.0,
    val attachment: Double = 40.0,
) {
    fun adjusted(
        mood: Double = 0.0,
        energy: Double = 0.0,
        boredom: Double = 0.0,
        anger: Double = 0.0,
        attachment: Double = 0.0,
    ) = SoulState(
        mood = clamp(this.mood + mood),
        energy = clamp(this.energy + energy),
        boredom = clamp(this.boredom + boredom),
        anger = clamp(this.anger + anger),
        attachment = clamp(this.attachment + attachment),
    )
}

/** Reakcije na poruke korisnika. */
enum class SoulEvent(
    val mood: Double = 0.0,
    val boredom: Double = 0.0,
    val anger: Double = 0.0,
    val attachment: Double = 0.0,
) {
    GREETING(mood = 5.0, attachment = 4.0, boredom = -10.0),
    JOKE(mood = 6.0, boredom = -8.0),
    PRAISE(mood = 8.0, attachment = 3.0, anger = -10.0),
    INSULT(mood = -10.0, anger = 20.0, attachment = -3.0),
    CHAT(boredom = -4.0, attachment = 1.0),
}

fun SoulState.after(event: SoulEvent): SoulState =
    adjusted(mood = event.mood, boredom = event.boredom, anger = event.anger, attachment = event.attachment)

/** Protok vremena bez obraćanja: raste dosada, ljutnja se smiruje, raspoloženje se vraća na dobro. */
fun SoulState.afterIdle(minutes: Double): SoulState {
    if (minutes <= 0) return this
    val idle = adjusted(
        boredom = 0.4 * minutes,
        energy = -0.1 * minutes,
        anger = -1.0 * minutes,
        attachment = -0.02 * minutes,
    )
    val drift = (MOOD_HOME - idle.mood) * minOf(1.0, 0.05 * minutes)
    return idle.adjusted(mood = drift)
}

/** Odmor (san): vraća energiju. */
fun SoulState.rested(minutes: Double): SoulState =
    if (minutes > 0) adjusted(energy = 0.5 * minutes) else this
