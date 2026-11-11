package io.androllm.feature.avatar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.androllm.core.soul.AvatarCommand
import io.androllm.core.soul.AvatarMotionDirector
import io.androllm.core.soul.AvatarMotionPlayer
import io.androllm.core.soul.AvatarPose
import io.androllm.core.soul.Facing
import io.androllm.core.soul.MotionGoal
import io.androllm.core.soul.MotionStep
import io.androllm.core.soul.Point
import io.androllm.core.soul.Rect as AvatarRect
import kotlin.math.roundToInt

/** Raspored u pikselima: ekran, dimenzije lika i zone koje lik ne smije zaklanjati. */
private data class AvatarLayout(
    val screen: AvatarRect,
    val width: Float,
    val height: Float,
    val avoid: List<AvatarRect>,
    val margin: Float,
) {
    /** Podrazumijevano mjesto: gornji desni ugao. */
    val home: AvatarRect
        get() = AvatarRect(screen.right - width - margin, screen.top + margin, screen.right - margin, screen.top + margin + height)
}

/**
 * Plutajući lik preko ekrana (Chat i Home). Stavlja se kao zadnje dijete u [Box] koji
 * pokriva ekran. Ne hvata dodire. Kad stigne naredba ("miči se", "sjedni", "nasloni se") ili
 * tastatura zakloni lik, [AvatarMotionDirector] odredi cilj, [AvatarMotionPlanner] napravi plan
 * (ustani, okreni se, hodaj, sjedni/nasloni se), a host ga izvodi po frejmovima. Lik ne klizi.
 *
 * Zone izbjegavanja: tastatura (visina iz [WindowInsets.ime]) i donja traka za unos
 * visine [bottomBarHeight] iznad nje. Visina trake je procjena koju daje ekran.
 */
@Composable
fun AvatarOverlayHost(
    modifier: Modifier = Modifier,
    bottomBarHeight: Dp = 0.dp,
    renderer: AvatarRenderer = remember { PlaceholderAvatarRenderer() },
    motionConfig: AvatarMotionConfig = remember { AvatarMotionConfig() },
    viewModel: AvatarViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    if (!settings.showAvatar) return

    val params by viewModel.params.collectAsStateWithLifecycle()
    val expression by viewModel.expression.collectAsStateWithLifecycle()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val avatarWidthDp = settings.avatarSize.dp.dp
        val avatarHeightDp = avatarWidthDp * 1.2f
        val widthPx = with(density) { avatarWidthDp.toPx() }
        val heightPx = with(density) { avatarHeightDp.toPx() }
        val marginPx = with(density) { 12.dp.toPx() }
        val barPx = with(density) { bottomBarHeight.toPx() }
        val imePx = WindowInsets.ime.getBottom(density).toFloat()

        val avoid = if (imePx > 0f || barPx > 0f) {
            listOf(AvatarRect(0f, (screenH - imePx - barPx).coerceAtLeast(0f), screenW, screenH))
        } else {
            emptyList()
        }
        val layout = AvatarLayout(AvatarRect(0f, 0f, screenW, screenH), widthPx, heightPx, avoid, marginPx)
        val latest by rememberUpdatedState(layout)

        // Trenutno stanje lika (lijevi gornji ugao, poza, okrenutost); null = početno mjesto, uspravan.
        var visual by remember { mutableStateOf<AvatarVisual?>(null) }
        // Plan koji se trenutno izvodi; novi plan prekida stari od trenutnog stanja.
        var session by remember { mutableStateOf<MotionSession?>(null) }
        val stridePx = widthPx * motionConfig.strideWidthFactor

        fun currentRect(l: AvatarLayout): AvatarRect {
            val p = visual?.position ?: return l.home
            val x = p.x.coerceIn(0f, (l.screen.right - l.width).coerceAtLeast(0f))
            val y = p.y.coerceIn(0f, (l.screen.bottom - l.height).coerceAtLeast(0f))
            return AvatarRect(x, y, x + l.width, y + l.height)
        }

        fun currentPose(): AvatarPose = visual?.motion?.pose ?: AvatarPose.STANDING

        fun startMotion(goal: MotionGoal) {
            val l = latest
            val cur = currentRect(l)
            val pose = currentPose()
            val facing = visual?.motion?.facing ?: Facing.FRONT
            val steps = AvatarMotionDirector.planTo(cur, pose, facing, goal, motionConfig.timings)
            session = MotionSession(steps, Point(cur.left, cur.top), pose, facing, goal)
        }

        // Naredbe iz chata.
        LaunchedEffect(viewModel) {
            viewModel.commands.collect { command ->
                val l = latest
                if (l.screen.width <= 0f || l.screen.height <= 0f) return@collect
                startMotion(
                    AvatarMotionDirector.resolve(
                        command, l.screen, currentRect(l), currentPose(), l.avoid, l.home, l.margin
                    )
                )
            }
        }

        // Tastatura ili traka za unos zaklanja lik: sam se pomjeri (ako sjedi, sjedne iznad nje).
        LaunchedEffect(avoid, screenW, screenH, widthPx, heightPx) {
            val l = latest
            if (l.screen.width <= 0f || l.screen.height <= 0f || l.avoid.isEmpty()) return@LaunchedEffect
            val current = currentRect(l)
            if (l.avoid.any { it.intersects(current) }) {
                val command = if (currentPose() == AvatarPose.SITTING) AvatarCommand.SIT_DOWN else AvatarCommand.MOVE_AWAY
                startMotion(
                    AvatarMotionDirector.resolve(
                        command, l.screen, current, currentPose(), l.avoid, l.home, l.margin
                    )
                )
            }
        }

        // Izvođenje plana po frejmovima: hod brzinom hoda, bez klizanja.
        LaunchedEffect(session) {
            val s = session ?: return@LaunchedEffect
            val startNanos = withFrameNanos { it }
            var finished = false
            while (!finished) {
                withFrameNanos { now ->
                    val elapsedMs = (now - startNanos) / 1_000_000L
                    val state = AvatarMotionPlayer.stateAt(s.steps, s.startPosition, s.startPose, s.startFacing, elapsedMs)
                    if (state.finished) {
                        // Na kraju lik stoji tačno na cilju (plan ne hoda za mali pomak).
                        visual = AvatarVisual(
                            Point(s.goal.target.left, s.goal.target.top),
                            AvatarMotionParams.at(state.pose, state.facing),
                        )
                        finished = true
                    } else {
                        visual = AvatarVisual(state.position, AvatarMotionParams.from(state, stridePx))
                    }
                    Unit
                }
            }
        }

        val rect = currentRect(layout)
        val motion = visual?.motion ?: AvatarMotionParams.Idle

        Box(
            modifier = Modifier
                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .width(avatarWidthDp)
                .height(avatarHeightDp)
                .semantics { contentDescription = "Alexa" }
        ) {
            renderer.Render(params, expression, Modifier.fillMaxSize(), motion)
        }
    }
}

/** Stanje lika na ekranu: lijevi gornji ugao u pikselima i parametri pokreta za renderer. */
private class AvatarVisual(val position: Point, val motion: AvatarMotionParams)

/** Jedan plan pokreta. Običan razred (poređenje po referenci), pa svaki novi plan ponovo pokreće izvođenje. */
private class MotionSession(
    val steps: List<MotionStep>,
    val startPosition: Point,
    val startPose: AvatarPose,
    val startFacing: Facing,
    val goal: MotionGoal,
)
