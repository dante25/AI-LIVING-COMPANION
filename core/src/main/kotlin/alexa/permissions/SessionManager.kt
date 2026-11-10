package alexa.permissions

const val CHAT_IDLE_MS = 15 * 60_000L   // 15 min tišine: sesija se zatvara
const val APP_IDLE_MS = 60 * 60_000L    // 1 h bez obraćanja: aplikacija se zatvara

/**
 * Faze:
 *  CHAT_ACTIVE  chat je otvoren, sesija traje, dozvole važe
 *  BACKGROUND   sesija je završena, aplikacija radi i sluša wake word
 *  CLOSED       aplikacija je zatvorena, wake word ne radi
 */
enum class Phase { CHAT_ACTIVE, BACKGROUND, CLOSED }

class SessionManager(private val gate: PermissionGate) {
    var phase: Phase = Phase.CLOSED
        private set

    private var lastContact = 0L
    private var sessionCounter = 0

    fun openChat(now: Long) {
        if (phase != Phase.CHAT_ACTIVE) startSession(now)
    }

    fun closeChat(now: Long) {
        if (phase == Phase.CHAT_ACTIVE) endSession(Phase.BACKGROUND)
    }

    /** Wake word radi samo dok je aplikacija u pozadini ili otvorena. */
    fun wakeWord(now: Long) {
        if (phase == Phase.BACKGROUND) startSession(now)
    }

    /** Bilo koja poruka ili odgovor resetuje tajmer tišine. */
    fun contact(now: Long) {
        if (phase == Phase.CHAT_ACTIVE) lastContact = now
    }

    /** Provjera tajmera. Zvati periodično ili pri svakoj radnji. */
    fun tick(now: Long) {
        if (phase == Phase.CLOSED) return
        if (now - lastContact >= APP_IDLE_MS) {
            endSession(Phase.CLOSED)
            return
        }
        if (phase == Phase.CHAT_ACTIVE && now - lastContact >= CHAT_IDLE_MS) {
            endSession(Phase.BACKGROUND)
        }
    }

    private fun startSession(now: Long) {
        sessionCounter++
        gate.beginSession(sessionCounter)
        lastContact = now
        phase = Phase.CHAT_ACTIVE
    }

    private fun endSession(next: Phase) {
        gate.endSession()
        phase = next
    }
}
