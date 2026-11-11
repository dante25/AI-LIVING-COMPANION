package io.androllm.core.soul

import kotlin.math.abs
import kotlin.math.sqrt

/** Poza u kojoj se lik trenutno nalazi. Hodati se može samo u pozi STANDING. */
enum class AvatarPose { STANDING, SITTING, LEANING, SLEEPING }

/** Na koju stranu je lik okrenut (pogled s kamere). */
enum class Facing { FRONT, LEFT, RIGHT, BACK }

data class Point(val x: Float, val y: Float)

/** Jedan korak pokreta. Renderer za svaki korak pušta odgovarajuću animaciju (ili najbližu koju ima). */
sealed interface MotionStep {
    val durationMs: Long

    data class StandUp(override val durationMs: Long) : MotionStep
    data class Turn(val to: Facing, override val durationMs: Long) : MotionStep
    data class Walk(val from: Point, val to: Point, val facing: Facing, override val durationMs: Long) : MotionStep
    data class SitDown(override val durationMs: Long) : MotionStep
    data class Lean(val toward: Facing, override val durationMs: Long) : MotionStep
    data class Pause(override val durationMs: Long) : MotionStep
}

/** Trajanja animacija i brzina hoda. Kad stigne pravi model, uzimaju se iz stvarnih klipova. */
data class ClipTimings(
    val standUpMs: Long = 1_200,
    val sitDownMs: Long = 1_400,
    val turnMs: Long = 500,
    val leanMs: Long = 900,
    val settleMs: Long = 300,
    /** Brzina hoda u pikselima u sekundi: ista kao brzina klipa hodanja, da se stopala ne kližu. */
    val walkSpeedPxPerSec: Float = 260f,
    val minWalkMs: Long = 600,
    /** Pomak manji od ovoga ne traži hodanje. */
    val minWalkDistancePx: Float = 40f,
)

/**
 * Pravi redoslijed pokreta: ako sjedi ili se naslanja, prvo ustane; okrene se prema smjeru hoda; ide brzinom hoda;
 * okrene se kako treba na cilju; pa sjedne, nasloni se ili stane. Nikad ne klizi sjedeći.
 */
object AvatarMotionPlanner {

    fun plan(
        from: Point,
        pose: AvatarPose,
        facing: Facing,
        to: Point,
        finalPose: AvatarPose,
        finalFacing: Facing = Facing.FRONT,
        timings: ClipTimings = ClipTimings(),
    ): List<MotionStep> {
        val steps = mutableListOf<MotionStep>()
        val dx = to.x - from.x
        val dy = to.y - from.y
        val distance = sqrt(dx * dx + dy * dy)
        val needsWalk = distance >= timings.minWalkDistancePx

        // Ista poza i isto mjesto: ništa za raditi.
        if (!needsWalk && pose == finalPose) return steps

        if (needsWalk && pose != AvatarPose.STANDING) {
            steps += MotionStep.StandUp(timings.standUpMs)
        } else if (!needsWalk && pose != AvatarPose.STANDING && finalPose != pose) {
            steps += MotionStep.StandUp(timings.standUpMs)
        }

        var current = facing

        if (needsWalk) {
            val walkFacing = facingFor(dx, dy)
            if (current != walkFacing) {
                steps += MotionStep.Turn(walkFacing, timings.turnMs)
                current = walkFacing
            }
            val ms = maxOf(timings.minWalkMs, (distance / timings.walkSpeedPxPerSec * 1000f).toLong())
            steps += MotionStep.Walk(from, to, walkFacing, ms)
        }

        if (finalFacing != current) {
            steps += MotionStep.Turn(finalFacing, timings.turnMs)
        }

        when (finalPose) {
            AvatarPose.SITTING, AvatarPose.SLEEPING -> steps += MotionStep.SitDown(timings.sitDownMs)
            AvatarPose.LEANING -> steps += MotionStep.Lean(finalFacing, timings.leanMs)
            AvatarPose.STANDING -> steps += MotionStep.Pause(timings.settleMs)
        }
        return steps
    }

    /** Lijevo i desno su profil; gore je leđima okrenuta (odlazi), dolje je licem prema kameri. */
    fun facingFor(dx: Float, dy: Float): Facing = when {
        abs(dx) >= abs(dy) -> if (dx < 0) Facing.LEFT else Facing.RIGHT
        dy < 0 -> Facing.BACK
        else -> Facing.FRONT
    }

    fun totalDurationMs(steps: List<MotionStep>): Long = steps.sumOf { it.durationMs }
}

/** Stanje lika u nekom trenutku izvođenja plana: renderer ga čita svaki frejm. */
data class MotionState(
    val position: Point,
    val pose: AvatarPose,
    val facing: Facing,
    /** Korak koji se trenutno izvodi (null kad je plan završen). */
    val step: MotionStep?,
    /** Napredak unutar koraka 0..1. */
    val progress: Float,
    val finished: Boolean,
)

/** Računa stanje lika u trenutku [elapsedMs] od početka plana. Čista funkcija, lako se testira. */
object AvatarMotionPlayer {

    fun stateAt(
        steps: List<MotionStep>,
        startPosition: Point,
        startPose: AvatarPose,
        startFacing: Facing,
        elapsedMs: Long,
    ): MotionState {
        var pos = startPosition
        var pose = startPose
        var facing = startFacing
        var remaining = elapsedMs.coerceAtLeast(0)

        for (step in steps) {
            if (remaining < step.durationMs) {
                val t = if (step.durationMs == 0L) 1f else remaining.toFloat() / step.durationMs
                val current = when (step) {
                    is MotionStep.Walk -> MotionState(
                        position = lerp(step.from, step.to, easeInOut(t)),
                        pose = AvatarPose.STANDING,
                        facing = step.facing,
                        step = step, progress = t, finished = false,
                    )
                    is MotionStep.Turn -> MotionState(pos, pose, step.to, step, t, false)
                    else -> MotionState(pos, pose, facing, step, t, false)
                }
                return current
            }
            remaining -= step.durationMs
            when (step) {
                is MotionStep.StandUp -> pose = AvatarPose.STANDING
                is MotionStep.Turn -> facing = step.to
                is MotionStep.Walk -> { pos = step.to; facing = step.facing; pose = AvatarPose.STANDING }
                is MotionStep.SitDown -> pose = AvatarPose.SITTING
                is MotionStep.Lean -> pose = AvatarPose.LEANING
                is MotionStep.Pause -> Unit
            }
        }
        return MotionState(pos, pose, facing, null, 1f, true)
    }

    private fun lerp(a: Point, b: Point, t: Float) = Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    /** Blago ubrzanje na početku i usporavanje na kraju, da hod ne izgleda mehanički. */
    private fun easeInOut(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }
}
