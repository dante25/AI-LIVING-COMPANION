package io.androllm.core.soul

enum class SoulAction { RESPOND, SULK, REFUSE, SEEK_ATTENTION, SLEEP }

/** Gruba klasifikacija poruke da bi se znalo koji događaj primijeniti. */
fun classify(text: String): SoulEvent {
    val t = text.lowercase()
    fun has(vararg words: String) = words.any { t.contains(it) }
    return when {
        has("glup", "idiot", "mrzim", "dosadna", "budala") -> SoulEvent.INSULT
        has("bravo", "super", "hvala", "odlična", "odlicna", "volim te") -> SoulEvent.PRAISE
        has("haha", "šala", "sala", "vic", "smij") -> SoulEvent.JOKE
        has("bok", "hej", "zdravo", "dobar dan", "ćao", "cao") -> SoulEvent.GREETING
        else -> SoulEvent.CHAT
    }
}

/**
 * Pravila ponašanja iz stanja. Ovdje se ne odlučuje o sigurnosti: odbijanje je samo
 * razigrano i nikad za važne ili sigurnosne zahtjeve (to piše u uputi modelu).
 */
fun decide(state: SoulState, event: SoulEvent): SoulAction = when {
    state.energy < 15 -> SoulAction.SLEEP
    event == SoulEvent.INSULT || state.anger >= 60 -> SoulAction.SULK
    state.anger >= 40 || state.mood < 35 -> SoulAction.REFUSE
    state.boredom >= 70 -> SoulAction.SEEK_ATTENTION
    else -> SoulAction.RESPOND
}
