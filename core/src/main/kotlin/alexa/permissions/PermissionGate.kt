package alexa.permissions

/**
 * Sve što Alexa može uraditi van chata prolazi kroz ovaj sloj.
 *
 * Pravila (iz razgovora i plana):
 *  - podrazumijevano je sve zabranjeno;
 *  - prekidač u postavkama mora biti uključen, inače se ništa ne pita;
 *  - dozvola se pamti samo do kraja sesije;
 *  - kamera uvijek pita, bez trajne dozvole;
 *  - potvrda za osjetljive radnje pita uvijek;
 *  - svaka odluka ide u dnevnik.
 */
enum class Capability { INTERNET, MICROPHONE, CAMERA, APP_CONTROL, WEB_SEARCH }

enum class Outcome { ALLOWED, DENIED_BY_SETTING, DENIED_BY_USER, CONFIRMED, REJECTED_BY_USER }

data class AuditEntry(
    val timeMillis: Long,
    val sessionId: Int,
    val description: String,
    val outcome: Outcome,
)

class AuditLog {
    private val items = mutableListOf<AuditEntry>()

    fun record(entry: AuditEntry) {
        items.add(entry)
    }

    fun entries(): List<AuditEntry> = items.toList()
}

/** Glavni prekidači u postavkama. Podrazumijevano je sve isključeno. */
class Settings {
    private val on = mutableSetOf<Capability>()

    fun set(capability: Capability, enabled: Boolean) {
        if (enabled) on.add(capability) else on.remove(capability)
    }

    fun isOn(capability: Capability): Boolean = capability in on
}

class PermissionGate(
    private val settings: Settings,
    private val audit: AuditLog,
) {
    private val sessionGrants = mutableSetOf<Capability>()
    private var sessionId = 0

    fun beginSession(id: Int) {
        sessionGrants.clear()
        sessionId = id
    }

    fun endSession() {
        sessionGrants.clear()
    }

    /** Da li je dozvola već data u ovoj sesiji (da se ne pita ponovo). */
    fun hasSessionGrant(capability: Capability): Boolean = capability in sessionGrants

    /**
     * Traži dozvolu za sposobnost. [ask] je korisnikov odgovor (dijalog).
     * Vraća true ako je radnja dozvoljena.
     */
    fun request(capability: Capability, now: Long, ask: (Capability) -> Boolean): Boolean {
        val outcome: Outcome
        val allowed: Boolean
        when {
            !settings.isOn(capability) -> {
                outcome = Outcome.DENIED_BY_SETTING
                allowed = false
            }
            capability in sessionGrants -> {
                outcome = Outcome.ALLOWED
                allowed = true
            }
            ask(capability) -> {
                outcome = Outcome.ALLOWED
                allowed = true
                // kamera nikad ne ostaje u sesiji: svaki put se pita
                if (capability != Capability.CAMERA) sessionGrants.add(capability)
            }
            else -> {
                outcome = Outcome.DENIED_BY_USER
                allowed = false
            }
        }
        audit.record(AuditEntry(now, sessionId, "dozvola: ${capability.name}", outcome))
        return allowed
    }

    /** Potvrda za osjetljivu radnju (slanje, brisanje). Uvijek pita. */
    fun confirmAction(description: String, now: Long, ask: (String) -> Boolean): Boolean {
        val yes = ask(description)
        audit.record(
            AuditEntry(
                now,
                sessionId,
                "potvrda: $description",
                if (yes) Outcome.CONFIRMED else Outcome.REJECTED_BY_USER,
            ),
        )
        return yes
    }
}
