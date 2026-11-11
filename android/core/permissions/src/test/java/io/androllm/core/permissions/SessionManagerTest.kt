package io.androllm.core.permissions

import io.androllm.core.tools.api.ToolPermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionManagerTest {

    private var now = 1_000_000L
    private lateinit var manager: SessionManager

    private val minute = 60_000L

    @Before
    fun setUp() {
        now = 1_000_000L
        manager = SessionManager { now }
    }

    @Test
    fun `grant survives while activity continues`() {
        manager.touch()
        manager.grant(ToolPermission.WEATHER)
        now += 10 * minute
        manager.touch()
        now += 10 * minute
        assertTrue(manager.isGranted(ToolPermission.WEATHER))
    }

    @Test
    fun `session ends after 15 minutes idle and resets grants`() {
        manager.touch()
        val first = manager.sessionId()
        manager.grant(ToolPermission.SEARCH)
        now += 15 * minute
        assertFalse(manager.isGranted(ToolPermission.SEARCH))
        assertFalse(manager.isSessionActive())
        manager.touch()
        assertNotEquals(first, manager.sessionId())
    }

    @Test
    fun `session stays alive just before 15 minutes`() {
        manager.touch()
        manager.grant(ToolPermission.SEARCH)
        now += 15 * minute - 1
        assertTrue(manager.isGranted(ToolPermission.SEARCH))
    }

    @Test
    fun `closing the chat ends the session and resets grants`() {
        manager.touch()
        val first = manager.sessionId()
        manager.grant(ToolPermission.MICROPHONE)
        manager.endSession()
        assertFalse(manager.isGranted(ToolPermission.MICROPHONE))
        manager.touch()
        assertNotEquals(first, manager.sessionId())
    }

    @Test
    fun `tick reports session end at 15 minutes`() {
        manager.touch()
        now += 14 * minute
        assertEquals(IdleAction.NONE, manager.tick())
        now += 1 * minute
        assertEquals(IdleAction.SESSION_ENDED, manager.tick())
        assertEquals(IdleAction.NONE, manager.tick())
    }

    @Test
    fun `tick asks to stop service after 60 minutes and only once`() {
        manager.touch()
        now += 15 * minute
        assertEquals(IdleAction.SESSION_ENDED, manager.tick())
        now += 44 * minute
        assertEquals(IdleAction.NONE, manager.tick())
        now += 1 * minute
        assertEquals(IdleAction.STOP_SERVICE, manager.tick())
        assertEquals(IdleAction.NONE, manager.tick())
    }

    @Test
    fun `activity before 60 minutes postpones service stop`() {
        manager.touch()
        now += 59 * minute
        manager.touch()
        now += 59 * minute
        assertTrue(manager.tick() != IdleAction.STOP_SERVICE)
        now += 1 * minute
        assertEquals(IdleAction.STOP_SERVICE, manager.tick())
    }

    @Test
    fun `touch re-arms the service stop signal`() {
        manager.touch()
        now += 60 * minute
        assertEquals(IdleAction.STOP_SERVICE, manager.tick())
        manager.touch()
        now += 60 * minute
        assertEquals(IdleAction.STOP_SERVICE, manager.tick())
    }
}
