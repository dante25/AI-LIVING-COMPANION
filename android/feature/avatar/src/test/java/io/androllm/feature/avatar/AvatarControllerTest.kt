package io.androllm.feature.avatar

import io.androllm.core.soul.SoulAction
import io.androllm.core.soul.SoulState
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarControllerTest {

    private fun choose(state: SoulState, action: SoulAction = SoulAction.RESPOND) =
        AvatarLogic.chooseExpression(state, action)

    @Test
    fun `visoka ljutnja daje ANGRY`() {
        assertEquals(AvatarExpression.ANGRY, choose(SoulState(anger = 80.0)))
    }

    @Test
    fun `SULK daje ANGRY`() {
        assertEquals(AvatarExpression.ANGRY, choose(SoulState(), SoulAction.SULK))
    }

    @Test
    fun `visoka dosada daje BORED`() {
        assertEquals(AvatarExpression.BORED, choose(SoulState(boredom = 75.0)))
    }

    @Test
    fun `niska energija i SLEEP daju SLEEPY`() {
        assertEquals(AvatarExpression.SLEEPY, choose(SoulState(energy = 10.0)))
        assertEquals(AvatarExpression.SLEEPY, choose(SoulState(), SoulAction.SLEEP))
    }

    @Test
    fun `dobro raspolozenje daje HAPPY`() {
        assertEquals(AvatarExpression.HAPPY, choose(SoulState(mood = 90.0)))
    }

    @Test
    fun `podrazumijevano stanje daje NEUTRAL`() {
        assertEquals(AvatarExpression.NEUTRAL, choose(SoulState()))
    }

    @Test
    fun `ljutnja ima prednost nad dobrim raspolozenjem`() {
        assertEquals(AvatarExpression.ANGRY, choose(SoulState(mood = 90.0, anger = 70.0)))
    }

    @Test
    fun `kontroler objavljuje izraz iz stanja`() {
        val c = AvatarController(Random(1))
        c.onSoul(SoulState(anger = 90.0), SoulAction.RESPOND)
        c.step(16)
        assertEquals(AvatarExpression.ANGRY, c.expression.value)
    }

    @Test
    fun `prvi treptaj je izmedju 3 i 6 sekundi`() {
        repeat(20) { seed ->
            val c = AvatarController(Random(seed))
            assertTrue(c.nextBlinkAtMs in 3_000L..6_000L)
        }
    }

    @Test
    fun `oci trepcu u predvidjenom trenutku`() {
        val c = AvatarController(Random(3))
        val blinkAt = c.nextBlinkAtMs
        var minOpenBefore = 1f
        var minOpenAfter = 1f
        var t = 16L
        while (t <= blinkAt + 400L) {
            val p = c.step(t)
            if (t < blinkAt) minOpenBefore = minOf(minOpenBefore, p.eyeLOpen) else minOpenAfter = minOf(minOpenAfter, p.eyeLOpen)
            t += 16L
        }
        assertTrue("prije treptaja oci su otvorene", minOpenBefore > 0.95f)
        assertTrue("u treptaju se oci zatvaraju", minOpenAfter < 0.3f)
        // Sljedeci treptaj je opet 3-6 s kasnije.
        assertTrue(c.nextBlinkAtMs - blinkAt in 3_000L..6_016L)
    }

    @Test
    fun `isti sjeme daje iste parametre`() {
        val a = AvatarController(Random(42))
        val b = AvatarController(Random(42))
        var pa = AvatarParams()
        var pb = AvatarParams()
        var t = 0L
        while (t < 10_000L) {
            t += 33L
            pa = a.step(t)
            pb = b.step(t)
        }
        assertEquals(pa, pb)
    }

    @Test
    fun `disanje je u rasponu nula do jedan i mijenja se`() {
        val c = AvatarController(Random(5))
        var min = 1f
        var max = 0f
        var t = 0L
        while (t < 8_000L) {
            t += 33L
            val b = c.step(t).breath
            min = minOf(min, b)
            max = maxOf(max, b)
        }
        assertTrue(min >= 0f && max <= 1f)
        assertTrue(max - min > 0.8f)
    }

    @Test
    fun `usta se otvaraju uz glasan govor i zatvaraju u tisini`() {
        val c = AvatarController(Random(5))
        c.onSpeechLevel(0.9f)
        var t = 0L
        var p = AvatarParams()
        repeat(15) { t += 33L; p = c.step(t) }
        assertTrue(p.mouthOpenY > 0.7f)
        c.onSpeechLevel(0f)
        repeat(30) { t += 33L; p = c.step(t) }
        assertTrue(p.mouthOpenY < 0.05f)
    }

    @Test
    fun `povremeno se pojavi PLAYFUL kad je inace neutralno`() {
        val c = AvatarController(Random(9))
        var seen = false
        var t = 0L
        while (t < 50_000L) {
            t += 100L
            c.step(t)
            if (c.expression.value == AvatarExpression.PLAYFUL) seen = true
        }
        assertTrue(seen)
    }

    @Test
    fun `react privremeno pregazi izraz`() {
        val c = AvatarController(Random(9))
        c.react(AvatarExpression.SURPRISED, nowMs = 0L, durationMs = 1_000L)
        c.step(100L)
        assertEquals(AvatarExpression.SURPRISED, c.expression.value)
        c.step(1_500L)
        assertEquals(AvatarExpression.NEUTRAL, c.expression.value)
    }

    @Test
    fun `glađenje usta raste prema cilju i ne prelazi ga`() {
        var m = 0f
        var last = 0f
        repeat(10) {
            m = AvatarLogic.smoothMouth(m, 0.8f, 30L)
            assertTrue(m >= last && m <= 0.8f)
            last = m
        }
        assertTrue(m > 0.5f)
    }

    @Test
    fun `usta se zatvaraju sporije nego sto se otvaraju`() {
        val opened = AvatarLogic.smoothMouth(0f, 1f, 30L)
        val closed = 1f - AvatarLogic.smoothMouth(1f, 0f, 30L)
        assertTrue(opened > closed)
    }

    @Test
    fun `mouthOpenFromPcm za tisinu daje nulu a za glasan zvuk visoku vrijednost`() {
        assertEquals(0f, AvatarLogic.mouthOpenFromPcm(FloatArray(480), 0f), 0f)
        val loud = FloatArray(480) { if (it % 2 == 0) 0.8f else -0.8f }
        var m = 0f
        repeat(5) { m = AvatarLogic.mouthOpenFromPcm(loud, m, 30L) }
        assertTrue(m > 0.6f)
        assertTrue(m <= 1f)
    }
}
