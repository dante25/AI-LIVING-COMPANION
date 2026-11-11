package io.androllm.core.soul

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarMotionTest {
    private val timings = ClipTimings()
    private val a = Point(100f, 1800f)
    private val b = Point(900f, 400f)

    private fun plan(from: Point, pose: AvatarPose, to: Point, final: AvatarPose, facing: Facing = Facing.FRONT) =
        AvatarMotionPlanner.plan(from, pose, facing, to, final)

    @Test fun sittingCharacterStandsUpBeforeWalking() {
        val steps = plan(a, AvatarPose.SITTING, b, AvatarPose.SITTING)
        assertTrue(steps.first() is MotionStep.StandUp)
        assertTrue(steps.indexOfFirst { it is MotionStep.Walk } > steps.indexOfFirst { it is MotionStep.StandUp })
    }

    @Test fun walkingEndsWithSittingWhenAsked() {
        val steps = plan(a, AvatarPose.STANDING, b, AvatarPose.SITTING)
        assertTrue(steps.last() is MotionStep.SitDown)
    }

    @Test fun neverWalksWhileSittingOrLeaning() {
        for (pose in AvatarPose.values()) {
            for (final in AvatarPose.values()) {
                val steps = plan(a, pose, b, final)
                var current = pose
                for (s in steps) {
                    if (s is MotionStep.Walk) assertEquals("hoda samo stojeći ($pose -> $final)", AvatarPose.STANDING, current)
                    current = when (s) {
                        is MotionStep.StandUp -> AvatarPose.STANDING
                        is MotionStep.SitDown -> AvatarPose.SITTING
                        is MotionStep.Lean -> AvatarPose.LEANING
                        else -> current
                    }
                }
            }
        }
    }

    @Test fun turnsTowardsWalkingDirectionFirst() {
        val steps = plan(Point(500f, 1000f), AvatarPose.STANDING, Point(900f, 1000f), AvatarPose.STANDING, Facing.FRONT)
        val turn = steps.first { it is MotionStep.Turn } as MotionStep.Turn
        assertEquals(Facing.RIGHT, turn.to)
        assertTrue(steps.indexOf(turn) < steps.indexOfFirst { it is MotionStep.Walk })
    }

    @Test fun walkDurationMatchesWalkSpeedSoFeetDoNotSlide() {
        val steps = plan(Point(0f, 0f), AvatarPose.STANDING, Point(520f, 0f), AvatarPose.STANDING)
        val walk = steps.filterIsInstance<MotionStep.Walk>().single()
        // 520 px pri 260 px/s = 2 s
        assertEquals(2_000L, walk.durationMs)
    }

    @Test fun faceFrontAtDestinationAfterWalking() {
        val steps = plan(a, AvatarPose.STANDING, b, AvatarPose.STANDING)
        val state = AvatarMotionPlayer.stateAt(steps, a, AvatarPose.STANDING, Facing.FRONT, AvatarMotionPlanner.totalDurationMs(steps))
        assertTrue(state.finished)
        assertEquals(Facing.FRONT, state.facing)
        assertEquals(b.x, state.position.x, 0.5f)
        assertEquals(b.y, state.position.y, 0.5f)
    }

    @Test fun leanAtTheEndGivesLeaningPose() {
        val steps = plan(a, AvatarPose.STANDING, b, AvatarPose.LEANING)
        val state = AvatarMotionPlayer.stateAt(steps, a, AvatarPose.STANDING, Facing.FRONT, AvatarMotionPlanner.totalDurationMs(steps))
        assertEquals(AvatarPose.LEANING, state.pose)
    }

    @Test fun positionMovesMonotonicallyDuringWalk() {
        val steps = plan(Point(0f, 0f), AvatarPose.STANDING, Point(800f, 0f), AvatarPose.STANDING)
        val walkStart = steps.takeWhile { it !is MotionStep.Walk }.sumOf { it.durationMs }
        val walk = steps.filterIsInstance<MotionStep.Walk>().single()
        var last = -1f
        var t = 0L
        while (t <= walk.durationMs) {
            val s = AvatarMotionPlayer.stateAt(steps, Point(0f, 0f), AvatarPose.STANDING, Facing.FRONT, walkStart + t)
            assertTrue(s.position.x >= last)
            last = s.position.x
            t += 100
        }
    }

    @Test fun noMovementNeededGivesEmptyPlan() {
        assertTrue(plan(a, AvatarPose.STANDING, a, AvatarPose.STANDING).isEmpty())
    }

    @Test fun walkingUpTheScreenFacesBack() {
        assertEquals(Facing.BACK, AvatarMotionPlanner.facingFor(10f, -400f))
        assertEquals(Facing.FRONT, AvatarMotionPlanner.facingFor(10f, 400f))
        assertEquals(Facing.LEFT, AvatarMotionPlanner.facingFor(-400f, 10f))
    }

    @Test fun duringStandUpThePositionStaysPut() {
        val steps = plan(a, AvatarPose.SITTING, b, AvatarPose.SITTING)
        val s = AvatarMotionPlayer.stateAt(steps, a, AvatarPose.SITTING, Facing.FRONT, 500)
        assertEquals(a, s.position)
        assertFalse(s.finished)
    }
}
