package io.androllm.feature.avatar

import io.androllm.core.soul.AvatarMotionPlanner
import io.androllm.core.soul.AvatarMotionPlayer
import io.androllm.core.soul.AvatarPose
import io.androllm.core.soul.ClipTimings
import io.androllm.core.soul.Facing
import io.androllm.core.soul.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarMotionParamsTest {

    @Test
    fun `sjedenje spusta tijelo prema napretku`() {
        val p = AvatarMotionParams(AvatarPose.STANDING, Facing.FRONT, 0f, 0.25f, MotionKind.SIT_DOWN)
        assertEquals(0.25f, p.sitAmount, 0.0001f)
    }

    @Test
    fun `ustajanje podize tijelo`() {
        val p = AvatarMotionParams(AvatarPose.SITTING, Facing.FRONT, 0f, 0.25f, MotionKind.STAND_UP)
        assertEquals(0.75f, p.sitAmount, 0.0001f)
        assertEquals(0f, p.leanAmount, 0.0001f)
    }

    @Test
    fun `sjedeci lik je potpuno spusten, stojeci nije`() {
        assertEquals(1f, AvatarMotionParams.at(AvatarPose.SITTING, Facing.FRONT).sitAmount, 0f)
        assertEquals(0f, AvatarMotionParams.at(AvatarPose.STANDING, Facing.FRONT).sitAmount, 0f)
    }

    @Test
    fun `naslanjanje se nagiba prema rubu`() {
        val leaning = AvatarMotionParams(AvatarPose.STANDING, Facing.LEFT, 0f, 0.5f, MotionKind.LEAN)
        assertEquals(0.5f, leaning.leanAmount, 0.0001f)
        assertEquals(1f, AvatarMotionParams.at(AvatarPose.LEANING, Facing.LEFT).leanAmount, 0f)
        val leaving = AvatarMotionParams(AvatarPose.LEANING, Facing.LEFT, 0f, 0.4f, MotionKind.STAND_UP)
        assertEquals(0.6f, leaving.leanAmount, 0.0001f)
    }

    @Test
    fun `iz plana - sjedi, hoda pa ponovo sjeda`() {
        val steps = AvatarMotionPlanner.plan(
            Point(0f, 0f), AvatarPose.SITTING, Facing.FRONT,
            Point(600f, 0f), AvatarPose.SITTING, Facing.FRONT, ClipTimings()
        )
        val kinds = mutableSetOf<MotionKind>()
        var walkPhaseSeen = false
        var t = 0L
        while (t < AvatarMotionPlanner.totalDurationMs(steps) + 50) {
            val state = AvatarMotionPlayer.stateAt(steps, Point(0f, 0f), AvatarPose.SITTING, Facing.FRONT, t)
            val p = AvatarMotionParams.from(state, 100f)
            kinds += p.kind
            if (p.isWalking && p.walkPhase > 0f) walkPhaseSeen = true
            if (p.isWalking) assertFalse(p.sitAmount > 0f)
            t += 20
        }
        assertTrue(MotionKind.STAND_UP in kinds && MotionKind.WALK in kinds && MotionKind.SIT_DOWN in kinds)
        assertTrue(walkPhaseSeen)
        val end = AvatarMotionParams.from(
            AvatarMotionPlayer.stateAt(steps, Point(0f, 0f), AvatarPose.SITTING, Facing.FRONT, 1_000_000), 100f
        )
        assertEquals(MotionKind.IDLE, end.kind)
        assertEquals(1f, end.sitAmount, 0f)
    }
}
