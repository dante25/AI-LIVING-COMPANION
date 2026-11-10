package alexa.soul

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SoulStateTest {
    @Test
    fun valuesStayInRange() {
        val s = SoulState(anger = 95.0)
        repeat(10) { s.onEvent("insult") }
        assertTrue(s.anger <= 100.0)
        assertTrue(s.mood >= 0.0)
    }

    @Test
    fun insultRaisesAngerAndLowersMood() {
        val s = SoulState()
        val (anger, mood) = s.anger to s.mood
        s.onEvent("insult")
        assertTrue(s.anger > anger)
        assertTrue(s.mood < mood)
    }

    @Test
    fun praiseCalmsAnger() {
        val s = SoulState(anger = 50.0)
        s.onEvent("praise")
        assertTrue(s.anger < 50.0)
    }

    @Test
    fun angerFadesAndMoodReturnsHome() {
        val s = SoulState(mood = 20.0, anger = 80.0)
        s.tick(240.0)
        assertEquals(0.0, s.anger)
        assertTrue(abs(s.mood - MOOD_HOME) < 5.0)
    }

    @Test
    fun boredomGrowsWhenIgnored() {
        val s = SoulState()
        val boredom = s.boredom
        s.tick(60.0)
        assertTrue(s.boredom > boredom)
    }

    @Test
    fun restRestoresEnergy() {
        val s = SoulState(energy = 10.0)
        s.rest(60.0)
        assertTrue(s.energy > 10.0)
    }

    @Test
    fun sameInputSameResult() {
        val a = SoulState()
        val b = SoulState()
        for (kind in listOf("greeting", "insult", "joke")) {
            a.onEvent(kind)
            b.onEvent(kind)
        }
        assertEquals(a, b)
    }

    @Test
    fun unknownEventIsRejected() {
        assertFailsWith<IllegalArgumentException> { SoulState().onEvent("nepoznato") }
    }
}

class DecisionTest {
    @Test
    fun normalStateResponds() = assertEquals("respond", decide(SoulState(), "chat").action)

    @Test
    fun lowEnergySleeps() = assertEquals("sleep", decide(SoulState(energy = 10.0), "chat").action)

    @Test
    fun insultGetsSulk() = assertEquals("sulk", decide(SoulState(), "insult").action)

    @Test
    fun highAngerSulksEvenOnGreeting() =
        assertEquals("sulk", decide(SoulState(anger = 65.0), "greeting").action)

    @Test
    fun highBoredomSeeksAttention() =
        assertEquals("seek_attention", decide(SoulState(boredom = 75.0), "chat").action)

    @Test
    fun ignoredForHoursSeeksAttention() {
        val s = SoulState()
        s.tick(200.0)
        assertEquals("seek_attention", decide(s, "chat").action)
    }

    @Test
    fun playfulRefusalOnlyWhenUpset() {
        assertEquals("respond", decide(SoulState(), "command", refusePlayfully = true).action)
        assertEquals("refuse", decide(SoulState(anger = 50.0), "command", refusePlayfully = true).action)
    }
}
