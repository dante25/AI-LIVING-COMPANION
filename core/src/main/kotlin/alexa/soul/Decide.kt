package alexa.soul

data class Decision(val action: String, val reason: String)

/**
 * Pravila koja odlučuju o ponašanju na osnovu stanja duše.
 * Sigurnosne i dozvole provjere žive u sloju dozvola (faza 3), ne ovdje.
 *
 * refusePlayfully: poruka je bezazlen zahtjev (npr. "pusti pjesmu").
 */
fun decide(state: SoulState, kind: String, refusePlayfully: Boolean = false): Decision {
    if (state.energy < 15) return Decision("sleep", "energija je pala ispod 15")

    if (kind == "insult" || state.anger >= 60) {
        return Decision("sulk", "ljutnja je visoka, odgovara kratko")
    }

    if (refusePlayfully && (state.anger >= 40 || state.mood < 35)) {
        return Decision("refuse", "ne da joj se, zahtjev je bezazlen")
    }

    if (state.boredom >= 70) return Decision("seek_attention", "dosadilo joj je, traži pažnju")

    return Decision("respond", "normalan odgovor")
}
