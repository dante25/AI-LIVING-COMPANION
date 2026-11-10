package alexa.behavior

import alexa.model.ALLOWED_INTENTS
import alexa.soul.SoulState

/**
 * Autonomno ponašanje: Alexa radi nešto i kad je niko ne zove, prema stanju duše.
 * Odluka je pravilo, ne LLM. Svako ponašanje ima namjeru iz dozvoljene liste
 * (ili nema ništa, za mirovanje i spavanje).
 */
enum class Behavior(val intent: String?) {
    IDLE(null),
    SIT("sjedni"),
    WALK_TO_SCREEN("priđi"),
    SLEEP(null),
}

fun chooseBehavior(state: SoulState, hourOfDay: Int): Behavior = when {
    state.energy < 15 -> Behavior.SLEEP
    hourOfDay in 0..5 && state.energy < 60 -> Behavior.SLEEP
    state.boredom >= 70 -> Behavior.WALK_TO_SCREEN
    state.energy < 40 -> Behavior.SIT
    else -> Behavior.IDLE
}

/** Provjera da svako ponašanje koristi samo dozvoljene namjere. */
fun behaviorIntentsAreAllowed(): Boolean =
    Behavior.entries.all { it.intent == null || it.intent in ALLOWED_INTENTS }
