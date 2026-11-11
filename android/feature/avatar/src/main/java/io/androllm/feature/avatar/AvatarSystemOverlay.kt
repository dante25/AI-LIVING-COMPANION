package io.androllm.feature.avatar

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.androllm.core.soul.AvatarCommand
import io.androllm.core.soul.AvatarMotionDirector
import io.androllm.core.soul.AvatarMotionPlayer
import io.androllm.core.soul.AvatarPose
import io.androllm.core.soul.Facing
import io.androllm.core.soul.MotionGoal
import io.androllm.core.soul.Point
import io.androllm.core.soul.Rect as AvatarRect

/**
 * Sadržaj sistemskog prozora lika. Prozor je velik koliko i lik, a pokret (hod) se izvodi
 * pomjeranjem samog prozora preko [moveTo]. [cancelTick] se povećava kad korisnik prevuče lik
 * i prekida plan pokreta. Naredba MOVE_AWAY ovdje ide u suprotnu vertikalnu polovinu ekrana.
 */
@Composable
internal fun SystemOverlayAvatar(
    session: AvatarSession,
    widthPx: Float,
    heightPx: Float,
    marginPx: Float,
    screen: () -> AvatarRect,
    position: () -> Point,
    moveTo: (Float, Float) -> Unit,
    onSettled: () -> Unit,
    cancelTick: Int,
    renderer: AvatarRenderer,
    motionConfig: AvatarMotionConfig = remember { AvatarMotionConfig() },
) {
    val params by session.params.collectAsState()
    val expression by session.expression.collectAsState()
    var motion by remember { mutableStateOf(AvatarMotionParams.Idle) }
    var plan by remember { mutableStateOf<MotionSession?>(null) }
    val stridePx = widthPx * motionConfig.strideWidthFactor

    fun currentRect(): AvatarRect {
        val p = position()
        return AvatarRect(p.x, p.y, p.x + widthPx, p.y + heightPx)
    }

    fun startMotion(goal: MotionGoal) {
        val cur = currentRect()
        val steps = AvatarMotionDirector.planTo(cur, motion.pose, motion.facing, goal, motionConfig.timings)
        plan = MotionSession(steps, Point(cur.left, cur.top), motion.pose, motion.facing, goal)
    }

    LaunchedEffect(session) {
        session.commands.collect { command ->
            val scr = screen()
            if (scr.width <= 0f || scr.height <= 0f) return@collect
            val cur = currentRect()
            val home = AvatarRect(scr.right - widthPx - marginPx, scr.top + marginPx, scr.right - marginPx, scr.top + marginPx + heightPx)
            if (command == AvatarCommand.MOVE_AWAY) {
                val topHalf = (cur.top + cur.bottom) / 2f < scr.height / 2f
                val y = if (topHalf) scr.bottom - heightPx - marginPx else scr.top + marginPx
                val target = AvatarRect(cur.left, y, cur.right, y + heightPx)
                startMotion(MotionGoal(target, AvatarPose.STANDING, Facing.FRONT))
            } else {
                startMotion(
                    AvatarMotionDirector.resolve(command, scr, cur, motion.pose, emptyList(), home, marginPx)
                )
            }
        }
    }

    // Korisnik je prevukao lik: prekini plan, ostani u trenutnoj pozi.
    LaunchedEffect(cancelTick) {
        plan = null
        motion = AvatarMotionParams.at(motion.pose, motion.facing)
    }

    LaunchedEffect(plan) {
        val s = plan ?: return@LaunchedEffect
        val startNanos = withFrameNanos { it }
        var finished = false
        while (!finished) {
            withFrameNanos { now ->
                val elapsedMs = (now - startNanos) / 1_000_000L
                val state = AvatarMotionPlayer.stateAt(s.steps, s.startPosition, s.startPose, s.startFacing, elapsedMs)
                if (state.finished) {
                    moveTo(s.goal.target.left, s.goal.target.top)
                    motion = AvatarMotionParams.at(state.pose, state.facing)
                    finished = true
                } else {
                    moveTo(state.position.x, state.position.y)
                    motion = AvatarMotionParams.from(state, stridePx)
                }
                Unit
            }
        }
        onSettled()
    }

    Box(modifier = Modifier.fillMaxSize().semantics { contentDescription = "Alexa" }) {
        renderer.Render(params, expression, Modifier.fillMaxSize(), motion)
    }
}

/**
 * Korijen sistemskog prozora: hvata prevlačenje prstom na razini Viewa (sirove koordinate ekrana),
 * jer se prozor pomjera ispod prsta pa lokalne koordinate Composea ne bi davale pravu razliku.
 * Običan dodir ne radi ništa.
 */
internal class DragFrameLayout(
    context: Context,
    private val onDrag: (dx: Float, dy: Float) -> Unit,
    private val onDragEnd: () -> Unit,
) : FrameLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    private fun begin(ev: MotionEvent) {
        downX = ev.rawX; downY = ev.rawY
        lastX = downX; lastY = downY
        dragging = false
    }

    private fun movedFar(ev: MotionEvent) =
        kotlin.math.abs(ev.rawX - downX) > slop || kotlin.math.abs(ev.rawY - downY) > slop

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> begin(ev)
            MotionEvent.ACTION_MOVE -> if (movedFar(ev)) {
                dragging = true
                lastX = ev.rawX; lastY = ev.rawY
                return true
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> begin(ev)
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && movedFar(ev)) {
                    dragging = true
                    lastX = ev.rawX; lastY = ev.rawY
                } else if (dragging) {
                    onDrag(ev.rawX - lastX, ev.rawY - lastY)
                    lastX = ev.rawX; lastY = ev.rawY
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) onDragEnd()
                dragging = false
            }
        }
        return true
    }
}
