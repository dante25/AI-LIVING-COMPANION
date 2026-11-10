package alexa.extras

import alexa.actions.ActionHandler
import alexa.actions.ActionKind
import alexa.actions.ActionOutcome
import alexa.actions.ActionResult
import alexa.actions.ActionRunner
import alexa.behavior.Behavior
import alexa.behavior.behaviorIntentsAreAllowed
import alexa.behavior.chooseBehavior
import alexa.memory.InMemoryMemoryStore
import alexa.memory.Message
import alexa.memory.MemoryKind
import alexa.memory.relationshipMemories
import alexa.permissions.AuditLog
import alexa.permissions.Capability
import alexa.permissions.PermissionGate
import alexa.permissions.Settings
import alexa.soul.SoulState
import alexa.voice.WakeWordGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WakeWordTest {
    @Test
    fun belowThresholdDoesNotWake() {
        assertFalse(WakeWordGate().onDetection(0.5, 0))
    }

    @Test
    fun aboveThresholdWakes() {
        assertTrue(WakeWordGate().onDetection(0.9, 0))
    }

    @Test
    fun cooldownStopsDoubleWake() {
        val gate = WakeWordGate()
        assertTrue(gate.onDetection(0.9, 0))
        assertFalse(gate.onDetection(0.9, 500))
        assertTrue(gate.onDetection(0.9, 2_500))
    }
}

class BehaviorTest {
    @Test
    fun lowEnergySleeps() = assertEquals(Behavior.SLEEP, chooseBehavior(SoulState(energy = 10.0), 12))

    @Test
    fun nightTirednessSleeps() = assertEquals(Behavior.SLEEP, chooseBehavior(SoulState(energy = 50.0), 3))

    @Test
    fun boredomWalksToScreen() = assertEquals(Behavior.WALK_TO_SCREEN, chooseBehavior(SoulState(boredom = 80.0), 12))

    @Test
    fun tiredSits() = assertEquals(Behavior.SIT, chooseBehavior(SoulState(energy = 30.0), 12))

    @Test
    fun normalIsIdle() = assertEquals(Behavior.IDLE, chooseBehavior(SoulState(), 12))

    @Test
    fun behaviorIntentsUseOnlyAllowedList() = assertTrue(behaviorIntentsAreAllowed())
}

class ActionTest {
    private class Recorder(private val result: ActionResult) : ActionHandler {
        var calls = 0
        override fun perform(kind: ActionKind, args: Map<String, String>): ActionResult {
            calls++
            return result
        }
    }

    private fun gate(vararg on: Capability): PermissionGate {
        val settings = Settings()
        on.forEach { settings.set(it, true) }
        return PermissionGate(settings, AuditLog())
    }

    @Test
    fun openAppDeniedWhenSettingOff() {
        val handler = Recorder(ActionResult(true, "ok"))
        val outcome = ActionRunner(gate(), handler).run(
            ActionKind.OPEN_APP, mapOf("app" to "Poweramp"), 0, { true }, { true },
        )
        assertIs<ActionOutcome.Denied>(outcome)
        assertEquals(0, handler.calls)
    }

    @Test
    fun webSearchNeedsBothInternetAndSearchPermission() {
        val handler = Recorder(ActionResult(true, "ok"))
        val outcome = ActionRunner(gate(Capability.WEB_SEARCH), handler).run(
            ActionKind.WEB_SEARCH, mapOf("query" to "vrijeme"), 0, { true }, { true },
        )
        assertEquals(ActionOutcome.Denied(Capability.INTERNET), outcome)
    }

    @Test
    fun sensitiveActionCancelledWithoutConfirmation() {
        val handler = Recorder(ActionResult(true, "poslano"))
        val outcome = ActionRunner(gate(Capability.APP_CONTROL), handler).run(
            ActionKind.SEND_MESSAGE, mapOf("to" to "Ana", "text" to "bok"), 0, { true }, { false },
        )
        assertEquals(ActionOutcome.Cancelled, outcome)
        assertEquals(0, handler.calls)
    }

    @Test
    fun failedResultIsReportedAsFailed() {
        val handler = Recorder(ActionResult(false, "aplikacija nije instalirana"))
        val outcome = ActionRunner(gate(Capability.APP_CONTROL), handler).run(
            ActionKind.OPEN_APP, mapOf("app" to "Poweramp"), 0, { true }, { true },
        )
        val done = assertIs<ActionOutcome.Done>(outcome)
        assertFalse(done.result.ok)
    }

    @Test
    fun missingArgumentIsInvalid() {
        val handler = Recorder(ActionResult(true, "ok"))
        val outcome = ActionRunner(gate(Capability.APP_CONTROL), handler).run(
            ActionKind.OPEN_APP, mapOf("app" to " "), 0, { true }, { true },
        )
        assertIs<ActionOutcome.Invalid>(outcome)
        assertEquals(0, handler.calls)
    }
}

class RelationshipMemoryTest {
    @Test
    fun relationshipKindsAreListedSeparately() {
        val store = InMemoryMemoryStore()
        store.addMessage(Message("m1", "c1", "user", "haha, najbolja šala", 1000))
        val joke = store.suggest("šala o kafi", listOf("m1"), 1000, MemoryKind.SHARED_MOMENT)
        val fact = store.suggest("Viktor radi petkom", listOf("m1"), 1000, MemoryKind.FACT)
        store.confirm(joke.id)
        store.confirm(fact.id)

        val relationship = store.relationshipMemories()
        assertEquals(listOf(joke.id), relationship.map { it.id })
    }

    @Test
    fun correctionKeepsKindOfOldMemory() {
        val store = InMemoryMemoryStore()
        store.addMessage(Message("m1", "c1", "user", "ne volim to", 1000))
        val old = store.suggest("ne voli kiselo", listOf("m1"), 1000, MemoryKind.DISLIKE)
        store.confirm(old.id)
        val fixed = store.correct(old.id, "ne voli jako kiselo", listOf("m1"), 2000)
        assertEquals(MemoryKind.DISLIKE, fixed.kind)
    }
}
