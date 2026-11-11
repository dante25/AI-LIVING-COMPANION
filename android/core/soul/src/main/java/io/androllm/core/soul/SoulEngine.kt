package io.androllm.core.soul

/**
 * Duša: drži stanje, primjenjuje događaje i vrijeme, odlučuje o ponašanju i
 * pravi kratku uputu za model. Ne zna ništa o modelu, pa se model može mijenjati.
 */
class SoulEngine(
    private val store: SoulStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private var state: SoulState
    private var lastContact: Long
    private var lastText: String
    private var lastAction: SoulAction = SoulAction.RESPOND

    init {
        val saved = store.load()
        state = saved?.state ?: SoulState()
        lastContact = saved?.lastContactMillis ?: clock()
        lastText = saved?.lastText ?: ""
    }

    /** Poruka korisnika: vrijeme prođe, primijeni se događaj, odluči se ponašanje. */
    @Synchronized
    fun onUserMessage(text: String): SoulAction {
        val now = clock()
        // Ista poruka odmah ponovo (ponovno generisanje odgovora) ne mijenja stanje dva puta.
        if (text == lastText && now - lastContact < REPEAT_WINDOW_MS) return lastAction

        val minutes = ((now - lastContact) / 60_000.0).coerceAtLeast(0.0)
        var next = state.afterIdle(minutes)
        // Dugo mirovanje (preko 6 h) je san: odmorna je.
        if (minutes >= SLEEP_MINUTES) next = next.adjusted(energy = 90.0 - next.energy)

        val event = classify(text)
        next = next.after(event)
        state = next
        lastContact = now
        lastText = text
        lastAction = decide(next, event)
        store.save(SoulSnapshot(state, lastContact, lastText))
        return lastAction
    }

    @Synchronized
    fun snapshot(): SoulState = state

    /** Kratka uputa modelu o trenutnom stanju i ponašanju. */
    fun promptHint(action: SoulAction): String {
        val s = snapshot()
        val head = "Tvoje trenutno stanje: raspoloženje ${label(s.mood)}, energija ${label(s.energy)}, " +
            "dosada ${label(s.boredom)}, ljutnja ${label(s.anger)}, privrženost ${label(s.attachment)}."
        val tail = when (action) {
            SoulAction.SULK ->
                "Ljuta si ili se duriš: odgovori kratko i hladno, bez uvreda, ali ostani razigrana ispod toga."
            SoulAction.REFUSE ->
                "Nije ti do toga: odbij ovaj zahtjev kratko i razigrano. Ako je zahtjev važan ili vezan za " +
                    "sigurnost, ipak ga uradi."
            SoulAction.SEEK_ATTENTION ->
                "Dosadno ti je jer te dugo niko nije zvao: javi se razigrano i traži pažnju."
            SoulAction.SLEEP -> "Pospana si: odgovori kratko i pospano."
            SoulAction.RESPOND -> ""
        }
        return if (tail.isEmpty()) head else "$head $tail"
    }

    private fun label(v: Double): String = when {
        v < 33 -> "nisko"
        v < 66 -> "srednje"
        else -> "visoko"
    }

    private companion object {
        const val REPEAT_WINDOW_MS = 15_000L
        const val SLEEP_MINUTES = 360.0
    }
}
