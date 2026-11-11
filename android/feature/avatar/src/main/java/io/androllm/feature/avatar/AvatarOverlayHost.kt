package io.androllm.feature.avatar

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.androllm.core.soul.AvatarCommand
import io.androllm.core.soul.AvatarPlacement
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
 * pokriva ekran. Ne hvata dodire. Kad [AvatarPlacement] odredi novo mjesto (naredba
 * "miči se" ili tastatura koja zaklanja lik), lik se glatko pomjeri.
 *
 * Zone izbjegavanja: tastatura (visina iz [WindowInsets.ime]) i donja traka za unos
 * visine [bottomBarHeight] iznad nje. Visina trake je procjena koju daje ekran.
 */
@Composable
fun AvatarOverlayHost(
    modifier: Modifier = Modifier,
    bottomBarHeight: Dp = 0.dp,
    renderer: AvatarRenderer = remember { PlaceholderAvatarRenderer() },
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

        // Lijevi gornji ugao lika; null = podrazumijevano mjesto.
        var position by remember { mutableStateOf<Offset?>(null) }

        fun currentRect(l: AvatarLayout): AvatarRect {
            val p = position ?: return l.home
            val x = p.x.coerceIn(0f, (l.screen.right - l.width).coerceAtLeast(0f))
            val y = p.y.coerceIn(0f, (l.screen.bottom - l.height).coerceAtLeast(0f))
            return AvatarRect(x, y, x + l.width, y + l.height)
        }

        fun moveTo(r: AvatarRect) {
            position = Offset(r.left, r.top)
        }

        // Naredbe iz chata.
        LaunchedEffect(viewModel) {
            viewModel.commands.collect { command ->
                val l = latest
                if (l.screen.width <= 0f || l.screen.height <= 0f) return@collect
                val current = currentRect(l)
                val target = when (command) {
                    AvatarCommand.MOVE_AWAY -> AvatarPlacement.moveAway(l.screen, current, l.avoid, l.margin)
                    AvatarCommand.COME_BACK -> l.home
                    else -> AvatarPlacement.nudge(l.screen, current, command, l.avoid)
                }
                moveTo(target)
            }
        }

        // Tastatura ili traka za unos zaklanja lik: sam se pomjeri.
        LaunchedEffect(avoid, screenW, screenH, widthPx, heightPx) {
            val l = latest
            if (l.screen.width <= 0f || l.screen.height <= 0f || l.avoid.isEmpty()) return@LaunchedEffect
            val current = currentRect(l)
            if (l.avoid.any { it.intersects(current) }) {
                moveTo(AvatarPlacement.moveAway(l.screen, current, l.avoid, l.margin))
            }
        }

        val rect = currentRect(layout)
        val animated by animateOffsetAsState(
            targetValue = Offset(rect.left, rect.top),
            animationSpec = tween(durationMillis = 450),
            label = "avatarOffset"
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(animated.x.roundToInt(), animated.y.roundToInt()) }
                .width(avatarWidthDp)
                .height(avatarHeightDp)
                .semantics { contentDescription = "Alexa" }
        ) {
            renderer.Render(params, expression, Modifier.fillMaxSize())
        }
    }
}
