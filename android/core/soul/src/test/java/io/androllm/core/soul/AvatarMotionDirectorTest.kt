package io.androllm.core.soul

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarMotionDirectorTest {
    private val screen = Rect(0f, 0f, 1080f, 2400f)
    private val bar = Rect(0f, 2200f, 1080f, 2400f)
    private val home = Rect(768f, 12f, 1068f, 412f)
    private val timings = ClipTimings()

    private fun resolve(
        command: AvatarCommand,
        current: Rect,
        pose: AvatarPose,
        avoid: List<Rect> = listOf(bar),
    ) = AvatarMotionDirector.resolve(command, screen, current, pose, avoid, home, 12f)

    @Test fun seatIsTopOfBarWhenBarIsPresent() {
        val seat = AvatarMotionDirector.seatFor(screen, Rect(400f, 300f, 700f, 700f), listOf(bar))
        assertEquals(2200f, seat.bottom, 0.001f)
        assertEquals(400f, seat.left, 0.001f)
    }

    @Test fun seatIsBottomOfScreenWithoutAvoidZones() {
        val seat = AvatarMotionDirector.seatFor(screen, Rect(400f, 300f, 700f, 700f), emptyList())
        assertEquals(2400f, seat.bottom, 0.001f)
    }

    @Test fun seatSkipsTopOfKeyboardWhenInputFieldIsAboveIt() {
        val keyboard = Rect(0f, 1500f, 1080f, 2400f)
        val input = Rect(0f, 1380f, 1080f, 1500f)
        val seat = AvatarMotionDirector.seatFor(screen, Rect(400f, 1000f, 700f, 1400f), listOf(keyboard, input))
        assertEquals(1380f, seat.bottom, 0.001f)
        assertTrue(listOf(keyboard, input).none { it.intersects(seat) })
    }

    @Test fun sitDownGoalIsSittingFacingFront() {
        val goal = resolve(AvatarCommand.SIT_DOWN, Rect(400f, 300f, 700f, 700f), AvatarPose.STANDING)
        assertEquals(AvatarPose.SITTING, goal.pose)
        assertEquals(Facing.FRONT, goal.facing)
    }

    @Test fun standUpStaysInPlace() {
        val current = Rect(400f, 1800f, 700f, 2200f)
        val goal = resolve(AvatarCommand.STAND_UP, current, AvatarPose.SITTING)
        assertEquals(current, goal.target)
        assertEquals(AvatarPose.STANDING, goal.pose)
        val steps = AvatarMotionDirector.planTo(current, AvatarPose.SITTING, Facing.FRONT, goal, timings)
        assertTrue(steps.first() is MotionStep.StandUp)
        assertTrue(steps.none { it is MotionStep.Walk })
    }

    @Test fun leanPicksNearestEdgeAndFacesIt() {
        val left = AvatarMotionDirector.leanFor(screen, Rect(100f, 500f, 400f, 900f), listOf(bar))
        assertEquals(0f, left.target.left, 0.001f)
        assertEquals(Facing.LEFT, left.facing)
        assertEquals(AvatarPose.LEANING, left.pose)

        val right = AvatarMotionDirector.leanFor(screen, Rect(700f, 500f, 1000f, 900f), listOf(bar))
        assertEquals(1080f, right.target.right, 0.001f)
        assertEquals(Facing.RIGHT, right.facing)
    }

    @Test fun leanDoesNotEndUpInsideBar() {
        val goal = AvatarMotionDirector.leanFor(screen, Rect(100f, 2000f, 400f, 2400f), listOf(bar))
        assertTrue(!bar.intersects(goal.target))
        assertEquals(2200f, goal.target.bottom, 0.001f)
    }

    @Test fun moveKeepsPose() {
        val current = Rect(16f, 16f, 316f, 416f)
        assertEquals(AvatarPose.STANDING, resolve(AvatarCommand.MOVE_AWAY, current, AvatarPose.STANDING).pose)
        assertEquals(AvatarPose.SITTING, resolve(AvatarCommand.MOVE_AWAY, current, AvatarPose.SITTING).pose)
        val lean = resolve(AvatarCommand.MOVE_AWAY, current, AvatarPose.LEANING)
        assertEquals(AvatarPose.LEANING, lean.pose)
        assertTrue(lean.target.left == 0f || lean.target.right == 1080f)
    }

    @Test fun sittingCharacterStandsWalksAndSitsAgain() {
        val current = Rect(16f, 16f, 316f, 416f)
        val goal = resolve(AvatarCommand.MOVE_AWAY, current, AvatarPose.SITTING)
        val steps = AvatarMotionDirector.planTo(current, AvatarPose.SITTING, Facing.FRONT, goal, timings)
        assertTrue(steps.first() is MotionStep.StandUp)
        assertTrue(steps.any { it is MotionStep.Walk })
        assertTrue(steps.last() is MotionStep.SitDown)
    }

    @Test fun standingCharacterStaysStandingAfterMove() {
        val current = Rect(16f, 16f, 316f, 416f)
        val goal = resolve(AvatarCommand.MOVE_AWAY, current, AvatarPose.STANDING)
        val steps = AvatarMotionDirector.planTo(current, AvatarPose.STANDING, Facing.FRONT, goal, timings)
        assertTrue(steps.none { it is MotionStep.StandUp || it is MotionStep.SitDown })
    }

    @Test fun walkPhaseGrowsWithCoveredDistance() {
        val walk = MotionStep.Walk(Point(0f, 0f), Point(300f, 400f), Facing.RIGHT, 2000)
        assertEquals(0f, AvatarWalkCycle.phase(walk, 0f, 200f), 0.0001f)
        // 500 px puta, ciklus od 200 px: 2.5 ciklusa na kraju.
        assertEquals(0.5f, AvatarWalkCycle.phase(walk, 1f, 200f), 0.0001f)
    }

    @Test fun walkPhaseIsMonotonicWithinFirstCycle() {
        val walk = MotionStep.Walk(Point(0f, 0f), Point(150f, 0f), Facing.RIGHT, 1000)
        var last = -1f
        for (i in 0..20) {
            val phase = AvatarWalkCycle.phase(walk, i / 20f, 200f)
            assertTrue(phase >= last)
            last = phase
        }
    }

    @Test fun walkPhaseWithNonPositiveStrideIsZero() {
        val walk = MotionStep.Walk(Point(0f, 0f), Point(150f, 0f), Facing.RIGHT, 1000)
        assertEquals(0f, AvatarWalkCycle.phase(walk, 0.5f, 0f), 0f)
    }
}
