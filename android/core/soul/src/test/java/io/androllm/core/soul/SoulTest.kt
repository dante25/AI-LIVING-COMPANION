package io.androllm.core.soul

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemoryStore : SoulStore {
    var saved: SoulSnapshot? = null
    override fun load() = saved
    override fun save(snapshot: SoulSnapshot) { saved = snapshot }
}

class SoulTest {
    @Test fun valuesStayInRange() {
        var s = SoulState(anger = 95.0)
        repeat(10) { s = s.after(SoulEvent.INSULT) }
        assertTrue(s.anger <= 100.0 && s.mood >= 0.0)
    }

    @Test fun angerFadesAndMoodReturnsHome() {
        val s = SoulState(mood = 20.0, anger = 80.0).afterIdle(240.0)
        assertEquals(0.0, s.anger, 0.0001)
        assertTrue(kotlin.math.abs(s.mood - MOOD_HOME) < 5.0)
    }

    @Test fun insultMakesHerSulk() {
        assertEquals(SoulAction.SULK, decide(SoulState(), SoulEvent.INSULT))
    }

    @Test fun boredomAfterLongSilenceSeeksAttention() {
        val s = SoulState().afterIdle(200.0)
        assertEquals(SoulAction.SEEK_ATTENTION, decide(s, SoulEvent.CHAT))
    }

    @Test fun lowEnergySleeps() {
        assertEquals(SoulAction.SLEEP, decide(SoulState(energy = 10.0), SoulEvent.CHAT))
    }

    @Test fun classifyRecognizesBosnianWords() {
        assertEquals(SoulEvent.GREETING, classify("Zdravo Alexa"))
        assertEquals(SoulEvent.PRAISE, classify("hvala ti"))
        assertEquals(SoulEvent.INSULT, classify("ti si glupa"))
        assertEquals(SoulEvent.CHAT, classify("koliko je sati"))
    }

    @Test fun engineAppliesEventsAndPersists() {
        val store = MemoryStore()
        var now = 0L
        val engine = SoulEngine(store, clock = { now })
        engine.onUserMessage("super si")
        assertTrue(store.saved!!.state.mood > 60.0)
    }

    @Test fun repeatedSameMessageDoesNotApplyEventTwice() {
        var now = 1_000_000L
        val engine = SoulEngine(MemoryStore(), clock = { now })
        engine.onUserMessage("glupa si")
        val anger1 = engine.snapshot().anger
        now += 2_000
        engine.onUserMessage("glupa si")
        assertEquals(anger1, engine.snapshot().anger, 0.0001)
    }

    @Test fun longSilenceBringsBoredomAndEnergyRestoresAfterSleep() {
        val store = MemoryStore()
        var now = 0L
        val engine = SoulEngine(store, clock = { now })
        engine.onUserMessage("bok")
        now += 8 * 60 * 60_000L
        val action = engine.onUserMessage("evo me")
        assertTrue(engine.snapshot().energy >= 80.0)
        assertEquals(SoulAction.SEEK_ATTENTION, action)
    }

    @Test fun refusalHintNeverTellsToRefuseImportantRequests() {
        val hint = SoulEngine(MemoryStore()).promptHint(SoulAction.REFUSE)
        assertTrue(hint.contains("sigurnost"))
    }
}
