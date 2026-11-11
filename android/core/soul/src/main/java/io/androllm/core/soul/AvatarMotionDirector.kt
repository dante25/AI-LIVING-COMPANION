package io.androllm.core.soul

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Cilj naredbe: gdje lik treba stati (lijevi gornji ugao pravougaonika), u kojoj pozi i okrenut kamo. */
data class MotionGoal(val target: Rect, val pose: AvatarPose, val facing: Facing)

/**
 * Pretvara naredbu korisnika u cilj pokreta (mjesto i krajnja poza). Čista logika bez Androida,
 * pa se testira bez emulatora. Sam pokret (ustani, okreni se, hodaj, sjedni) pravi [AvatarMotionPlanner].
 */
object AvatarMotionDirector {

    fun resolve(
        command: AvatarCommand,
        screen: Rect,
        current: Rect,
        currentPose: AvatarPose,
        avoid: List<Rect>,
        home: Rect,
        margin: Float,
    ): MotionGoal = when (command) {
        AvatarCommand.SIT_DOWN -> MotionGoal(seatFor(screen, current, avoid), AvatarPose.SITTING, Facing.FRONT)
        AvatarCommand.STAND_UP -> MotionGoal(current, AvatarPose.STANDING, Facing.FRONT)
        AvatarCommand.LEAN -> leanFor(screen, current, avoid)
        else -> {
            val target = when (command) {
                AvatarCommand.MOVE_AWAY -> AvatarPlacement.moveAway(screen, current, avoid, margin)
                AvatarCommand.COME_BACK -> home
                else -> AvatarPlacement.nudge(screen, current, command, avoid)
            }
            keepPose(screen, target, currentPose, avoid)
        }
    }

    /** Nakon pomjeranja lik zadržava istu krajnju pozu: sjedio je pa sjeda, stajao je pa stoji. */
    fun keepPose(screen: Rect, target: Rect, pose: AvatarPose, avoid: List<Rect>): MotionGoal = when (pose) {
        AvatarPose.STANDING -> MotionGoal(target, AvatarPose.STANDING, Facing.FRONT)
        AvatarPose.SITTING -> MotionGoal(target, AvatarPose.SITTING, Facing.FRONT)
        AvatarPose.SLEEPING -> MotionGoal(target, AvatarPose.SLEEPING, Facing.FRONT)
        AvatarPose.LEANING -> leanFor(screen, target, avoid)
    }

    /**
     * Najbliže sjedište: gornja ivica zone koju treba izbjegavati (traka za unos ili tastatura),
     * a bez nje donja ivica ekrana. Lik ostaje na istoj horizontalnoj poziciji.
     */
    fun seatFor(screen: Rect, current: Rect, avoid: List<Rect>): Rect {
        val x = current.left.coerceIn(screen.left, max(screen.left, screen.right - current.width))
        fun rectAt(seatY: Float) = Rect(x, seatY - current.height, x + current.width, seatY)

        val candidates = avoid.map { it.top }
            .filter { it <= screen.bottom && it - current.height >= screen.top }
            .map { rectAt(it) }
            .filter { r -> avoid.none { it.intersects(r) } }
        val best = candidates.minByOrNull { abs(it.bottom - current.bottom) }
        return best ?: rectAt(screen.bottom)
    }

    /** Naslanjanje uz bliži lijevi ili desni rub ekrana, okrenut ka rubu. */
    fun leanFor(screen: Rect, current: Rect, avoid: List<Rect>): MotionGoal {
        val toLeft = current.centerX < screen.centerX
        val x = if (toLeft) screen.left else screen.right - current.width
        var y = current.top.coerceIn(screen.top, max(screen.top, screen.bottom - current.height))
        var rect = Rect(x, y, x + current.width, y + current.height)
        val blocking = avoid.filter { it.intersects(rect) }
        if (blocking.isNotEmpty()) {
            val top = blocking.minOf { it.top }
            y = max(screen.top, top - current.height)
            rect = Rect(x, y, x + current.width, y + current.height)
        }
        return MotionGoal(rect, AvatarPose.LEANING, if (toLeft) Facing.LEFT else Facing.RIGHT)
    }

    /** Plan pokreta od trenutnog stanja do cilja. Prazan plan znači da nema šta izvoditi. */
    fun planTo(
        current: Rect,
        pose: AvatarPose,
        facing: Facing,
        goal: MotionGoal,
        timings: ClipTimings,
    ): List<MotionStep> = AvatarMotionPlanner.plan(
        from = Point(current.left, current.top),
        pose = pose,
        facing = facing,
        to = Point(goal.target.left, goal.target.top),
        finalPose = goal.pose,
        finalFacing = goal.facing,
        timings = timings,
    )
}

/** Ciklus koraka: faza 0..1 raste srazmjerno prijeđenom putu, pa se noge ne kližu po podu. */
object AvatarWalkCycle {

    /** Faza ciklusa koraka tokom [walk] u trenutku napretka [progress] (0..1); jedan ciklus = [stridePx] puta. */
    fun phase(walk: MotionStep.Walk, progress: Float, stridePx: Float): Float {
        if (stridePx <= 0f) return 0f
        val dx = walk.to.x - walk.from.x
        val dy = walk.to.y - walk.from.y
        val covered = sqrt(dx * dx + dy * dy) * AvatarMotionPlayer.easeInOut(progress)
        val cycles = covered / stridePx
        return cycles - kotlin.math.floor(cycles)
    }

}
