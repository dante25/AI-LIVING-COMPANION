package alexa.permissions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionGateTest {
    private fun gate(vararg enabled: Capability): Pair<PermissionGate, AuditLog> {
        val settings = Settings()
        enabled.forEach { settings.set(it, true) }
        val audit = AuditLog()
        return PermissionGate(settings, audit) to audit
    }

    @Test
    fun deniedWhenSettingIsOffAndUserIsNotAsked() {
        val (gate, audit) = gate()
        gate.beginSession(1)
        var asked = 0
        val allowed = gate.request(Capability.INTERNET, 0) { asked++; true }
        assertFalse(allowed)
        assertEquals(0, asked)
        assertEquals(Outcome.DENIED_BY_SETTING, audit.entries().single().outcome)
    }

    @Test
    fun askedOnceThenRememberedWithinSession() {
        val (gate, _) = gate(Capability.INTERNET)
        gate.beginSession(1)
        var asked = 0
        gate.request(Capability.INTERNET, 0) { asked++; true }
        gate.request(Capability.INTERNET, 1) { asked++; true }
        assertEquals(1, asked)
    }

    @Test
    fun sessionEndResetsGrants() {
        val (gate, _) = gate(Capability.INTERNET)
        gate.beginSession(1)
        gate.request(Capability.INTERNET, 0) { true }
        gate.endSession()
        gate.beginSession(2)
        var asked = 0
        gate.request(Capability.INTERNET, 1) { asked++; true }
        assertEquals(1, asked)
    }

    @Test
    fun cameraAlwaysAsks() {
        val (gate, _) = gate(Capability.CAMERA)
        gate.beginSession(1)
        var asked = 0
        gate.request(Capability.CAMERA, 0) { asked++; true }
        gate.request(Capability.CAMERA, 1) { asked++; true }
        assertEquals(2, asked)
    }

    @Test
    fun rejectedAskIsNotRemembered() {
        val (gate, _) = gate(Capability.APP_CONTROL)
        gate.beginSession(1)
        assertFalse(gate.request(Capability.APP_CONTROL, 0) { false })
        var asked = 0
        assertTrue(gate.request(Capability.APP_CONTROL, 1) { asked++; true })
        assertEquals(1, asked)
    }

    @Test
    fun confirmActionAlwaysAsksAndIsAudited() {
        val (gate, audit) = gate()
        gate.beginSession(1)
        var asked = 0
        assertTrue(gate.confirmAction("obriši razgovor", 0) { asked++; true })
        assertFalse(gate.confirmAction("pošalji poruku", 1) { asked++; false })
        assertEquals(2, asked)
        assertEquals(
            listOf(Outcome.CONFIRMED, Outcome.REJECTED_BY_USER),
            audit.entries().map { it.outcome },
        )
    }

    @Test
    fun everyDecisionIsAuditedWithSession() {
        val (gate, audit) = gate(Capability.WEB_SEARCH)
        gate.beginSession(7)
        gate.request(Capability.WEB_SEARCH, 100) { true }
        val entry = audit.entries().single()
        assertEquals(7, entry.sessionId)
        assertEquals(100L, entry.timeMillis)
        assertEquals(Outcome.ALLOWED, entry.outcome)
    }
}
