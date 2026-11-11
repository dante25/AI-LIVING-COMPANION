package io.androllm.core.tools.policy

import io.androllm.core.tools.api.ToolPermission
import io.androllm.core.tools.settings.AutomationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionPolicyTest {

    private class FakeSession : ToolSessionState {
        val granted = mutableSetOf<ToolPermission>()
        override fun touch() = Unit
        override fun sessionId(): Long = 1L
        override fun isGranted(permission: ToolPermission) = permission in granted
        override fun grant(permission: ToolPermission) {
            granted += permission
        }
    }

    private val session = FakeSession()

    private fun policies(vararg pairs: Pair<ToolPermission, PermissionPolicy>) =
        pairs.associate { it.first.name to it.second }

    @Test
    fun `off blocks the tool`() {
        val verdict = PermissionPolicyEvaluator.evaluate(
            ToolPermission.NOTES, policies(ToolPermission.NOTES to PermissionPolicy.OFF), session
        )
        assertEquals(PolicyVerdict.Blocked(ToolPermission.NOTES), verdict)
    }

    @Test
    fun `ask every time always asks and is never remembered`() {
        val p = policies(ToolPermission.NOTES to PermissionPolicy.ASK_EVERY_TIME)
        session.granted += ToolPermission.NOTES
        val verdict = PermissionPolicyEvaluator.evaluate(ToolPermission.NOTES, p, session)
        assertEquals(PolicyVerdict.Ask(emptyList()), verdict)
    }

    @Test
    fun `session policy asks once then allows`() {
        val p = policies(ToolPermission.NOTES to PermissionPolicy.SESSION)
        val first = PermissionPolicyEvaluator.evaluate(ToolPermission.NOTES, p, session)
        assertEquals(PolicyVerdict.Ask(listOf(ToolPermission.NOTES)), first)
        session.grant(ToolPermission.NOTES)
        assertEquals(PolicyVerdict.Allow, PermissionPolicyEvaluator.evaluate(ToolPermission.NOTES, p, session))
    }

    @Test
    fun `reset session asks again`() {
        val p = policies(ToolPermission.NOTES to PermissionPolicy.SESSION)
        session.grant(ToolPermission.NOTES)
        session.granted.clear()
        assertTrue(PermissionPolicyEvaluator.evaluate(ToolPermission.NOTES, p, session) is PolicyVerdict.Ask)
    }

    @Test
    fun `camera is never persistent`() {
        assertEquals(
            PermissionPolicy.ASK_EVERY_TIME,
            PermissionPolicy.effective(ToolPermission.CAMERA, PermissionPolicy.SESSION)
        )
        assertEquals(PermissionPolicy.ASK_EVERY_TIME, PermissionPolicy.effective(ToolPermission.CAMERA, null))
        assertFalse(PermissionPolicy.SESSION in PermissionPolicy.optionsFor(ToolPermission.CAMERA))
        session.grant(ToolPermission.CAMERA)
        val verdict = PermissionPolicyEvaluator.evaluate(
            ToolPermission.CAMERA, policies(ToolPermission.CAMERA to PermissionPolicy.SESSION), session
        )
        assertEquals(PolicyVerdict.Ask(emptyList()), verdict)
    }

    @Test
    fun `umbrella internet policy also applies to weather`() {
        val p = policies(
            ToolPermission.WEATHER to PermissionPolicy.SESSION,
            ToolPermission.INTERNET to PermissionPolicy.OFF
        )
        assertEquals(PolicyVerdict.Blocked(ToolPermission.INTERNET), PermissionPolicyEvaluator.evaluate(ToolPermission.WEATHER, p, session))
    }

    @Test
    fun `umbrella microphone policy applies to voice recorder`() {
        val p = policies(
            ToolPermission.VOICE_RECORDER to PermissionPolicy.SESSION,
            ToolPermission.MICROPHONE to PermissionPolicy.SESSION
        )
        val verdict = PermissionPolicyEvaluator.evaluate(ToolPermission.VOICE_RECORDER, p, session)
        assertEquals(
            PolicyVerdict.Ask(listOf(ToolPermission.VOICE_RECORDER, ToolPermission.MICROPHONE)),
            verdict
        )
    }

    @Test
    fun `tool without permission is never gated`() {
        assertEquals(PolicyVerdict.Allow, PermissionPolicyEvaluator.evaluate(null, emptyMap(), session))
    }

    @Test
    fun `settings default is session and camera default is ask`() {
        val s = AutomationSettings()
        assertEquals(PermissionPolicy.SESSION, s.policyFor(ToolPermission.WEATHER))
        assertEquals(PermissionPolicy.ASK_EVERY_TIME, s.policyFor(ToolPermission.CAMERA))
    }

    @Test
    fun `model download is not a gated permission`() {
        assertTrue(ToolPermission.entries.none { it.name.contains("DOWNLOAD") || it.name.contains("MODEL") })
    }
}
