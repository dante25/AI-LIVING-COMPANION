package alexa.permissions

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionManagerTest {
    private val minute = 60_000L

    private fun setup(): Triple<SessionManager, PermissionGate, Settings> {
        val settings = Settings()
        settings.set(Capability.INTERNET, true)
        val gate = PermissionGate(settings, AuditLog())
        return Triple(SessionManager(gate), gate, settings)
    }

    @Test
    fun closingChatMovesToBackgroundAndResetsGrants() {
        val (sm, gate, _) = setup()
        sm.openChat(0)
        gate.request(Capability.INTERNET, 0) { true }
        sm.closeChat(minute)
        assertEquals(Phase.BACKGROUND, sm.phase)
    }

    @Test
    fun fifteenMinutesOfSilenceEndsSession() {
        val (sm, _, _) = setup()
        sm.openChat(0)
        sm.tick(14 * minute)
        assertEquals(Phase.CHAT_ACTIVE, sm.phase)
        sm.tick(15 * minute)
        assertEquals(Phase.BACKGROUND, sm.phase)
    }

    @Test
    fun contactResetsSilenceTimer() {
        val (sm, _, _) = setup()
        sm.openChat(0)
        sm.contact(10 * minute)
        sm.tick(20 * minute) // 10 min od zadnjeg kontakta: još aktivna
        assertEquals(Phase.CHAT_ACTIVE, sm.phase)
    }

    @Test
    fun oneHourWithoutContactClosesApp() {
        val (sm, _, _) = setup()
        sm.openChat(0)
        sm.closeChat(minute)
        sm.tick(59 * minute)
        assertEquals(Phase.BACKGROUND, sm.phase)
        sm.tick(60 * minute)
        assertEquals(Phase.CLOSED, sm.phase)
    }

    @Test
    fun wakeWordStartsNewSessionFromBackground() {
        val (sm, gate, _) = setup()
        sm.openChat(0)
        sm.closeChat(minute)
        sm.wakeWord(20 * minute)
        assertEquals(Phase.CHAT_ACTIVE, sm.phase)
    }

    @Test
    fun wakeWordIsIgnoredWhenAppIsClosed() {
        val (sm, _, _) = setup()
        sm.wakeWord(0)
        assertEquals(Phase.CLOSED, sm.phase)
    }

    @Test
    fun openingChatWhileActiveDoesNotRestartSession() {
        val (sm, _, _) = setup()
        sm.openChat(0)
        sm.openChat(5 * minute)
        sm.tick(15 * minute) // zadnji kontakt je i dalje 0, pa sesija završava
        assertEquals(Phase.BACKGROUND, sm.phase)
    }
}
