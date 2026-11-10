package alexa.soul

/** Brojčano stanje Alexine duše. Sve vrijednosti su u rasponu 0–100. */
const val MOOD_HOME = 60.0

private fun clamp(value: Double): Double = value.coerceIn(0.0, 100.0)

data class SoulState(
    var mood: Double = 60.0,        // raspoloženje
    var energy: Double = 80.0,      // energija
    var boredom: Double = 10.0,     // dosada
    var anger: Double = 0.0,        // ljutnja
    var attachment: Double = 40.0,  // privrženost
) {
    init {
        mood = clamp(mood)
        energy = clamp(energy)
        boredom = clamp(boredom)
        anger = clamp(anger)
        attachment = clamp(attachment)
    }

    fun apply(
        mood: Double = 0.0,
        energy: Double = 0.0,
        boredom: Double = 0.0,
        anger: Double = 0.0,
        attachment: Double = 0.0,
    ) {
        this.mood = clamp(this.mood + mood)
        this.energy = clamp(this.energy + energy)
        this.boredom = clamp(this.boredom + boredom)
        this.anger = clamp(this.anger + anger)
        this.attachment = clamp(this.attachment + attachment)
    }
}

/** Promjena stanja (može biti negativna; ne ograničava se dok se ne primijeni). */
private data class Delta(
    val mood: Double = 0.0,
    val boredom: Double = 0.0,
    val anger: Double = 0.0,
    val attachment: Double = 0.0,
)

/** Reakcije na poruke korisnika. */
private val EVENTS: Map<String, Delta> = mapOf(
    "greeting" to Delta(mood = 5.0, attachment = 4.0, boredom = -10.0),
    "joke" to Delta(mood = 6.0, boredom = -8.0),
    "praise" to Delta(mood = 8.0, attachment = 3.0, anger = -10.0),
    "insult" to Delta(mood = -10.0, anger = 20.0, attachment = -3.0),
    "command" to Delta(boredom = -3.0, attachment = 1.0),
    "chat" to Delta(boredom = -4.0, attachment = 1.0),
)

fun SoulState.onEvent(kind: String) {
    val d = EVENTS[kind] ?: throw IllegalArgumentException("Nepoznat događaj: $kind")
    apply(mood = d.mood, boredom = d.boredom, anger = d.anger, attachment = d.attachment)
}

/** Protok vremena bez obraćanja: raste dosada, ljutnja se smiruje. */
fun SoulState.tick(minutes: Double) {
    if (minutes <= 0) return
    apply(
        boredom = 0.4 * minutes,
        energy = -0.1 * minutes,
        anger = -1.0 * minutes,
        attachment = -0.02 * minutes,
    )
    // raspoloženje se polako vraća na dobro raspoloženje
    val drift = (MOOD_HOME - mood) * minOf(1.0, 0.05 * minutes)
    apply(mood = drift)
}

/** Odmor (spavanje ili idle): vraća energiju. */
fun SoulState.rest(minutes: Double) {
    if (minutes > 0) apply(energy = 0.5 * minutes)
}
