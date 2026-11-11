package io.androllm.feature.avatar

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import io.androllm.core.soul.Facing
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Probni lik nacrtan kodom (stilizovana djevojka). Crta se u zamišljenom prostoru
 * 100 x 120 jedinica koji se uklapa u dostupnu površinu, pa se vidi na svakoj veličini.
 */
class PlaceholderAvatarRenderer : AvatarRenderer {

    @Composable
    override fun Render(
        params: AvatarParams,
        expression: AvatarExpression,
        modifier: Modifier,
        motion: AvatarMotionParams,
    ) {
        Canvas(modifier = modifier) {
            val fit = min(size.width / DESIGN_W, size.height / DESIGN_H)
            val dx = (size.width - DESIGN_W * fit) / 2f
            val dy = (size.height - DESIGN_H * fit) / 2f
            withTransform({
                translate(dx, dy)
                scale(fit, fit, pivot = Offset.Zero)
            }) {
                drawAvatar(params, expression, motion)
            }
        }
    }

    private fun DrawScope.drawAvatar(p: AvatarParams, expression: AvatarExpression, motion: AvatarMotionParams) {
        val sit = motion.sitAmount
        val lean = motion.leanAmount
        val facing = motion.facing
        val side = facing == Facing.LEFT || facing == Facing.RIGHT
        // Smjer prema kojem je lik okrenut / nagnut: -1 lijevo, 1 desno.
        val dir = if (facing == Facing.LEFT) -1f else 1f
        val theta = (motion.walkPhase * 2.0 * PI).toFloat()
        val swing = if (motion.isWalking) sin(theta) else 0f
        val bounce = if (motion.isWalking) -abs(swing) * 2f else 0f
        val drop = SIT_DROP * sit

        // Cijeli lik je smanjen da ispod haljine ima mjesta za noge.
        withTransform({
            scale(FIGURE_SCALE, FIGURE_SCALE, pivot = Offset(50f, 0f))
        }) {
            withTransform({
                // Nagib prema rubu oko stopala, pa blagi pomak ka rubu.
                rotate(dir * LEAN_DEGREES * lean, pivot = Offset(50f, FOOT_Y))
                translate(dir * 5f * lean, 0f)
                if (side) scale(0.82f, 1f, pivot = Offset(50f, 0f))
            }) {
                withTransform({ translate(0f, bounce + drop) }) {
                    drawUpperBody(p, expression, facing, dir)
                }
                drawLegs(sit, swing, side, dir)
                if (lean > 0.02f) drawLeaningArm(dir, lean, drop)
            }
        }
    }

    /** Noge: ravne kad stoji, savijene (koljena naprijed) kad sjedi, naizmjenično podizanje pri hodu. */
    private fun DrawScope.drawLegs(sit: Float, swing: Float, side: Boolean, dir: Float) {
        for (leg in intArrayOf(-1, 1)) {
            val sign = leg.toFloat()
            val x = 50f + sign * 8f
            val hipY = LEG_TOP_Y + SIT_DROP * sit
            // Podizanje stopala: lijeva noga kad je swing > 0, desna kad je < 0.
            val lift = max(0f, swing * -sign) * 5f
            val reach = if (side) swing * sign * 7f else 0f
            val footX = x + reach
            val footY = FOOT_Y - lift
            val midY = (hipY + footY) / 2f
            val kneeX = x + reach * 0.5f
            val kneeY = midY + (hipY - midY) * sit
            val path = Path().apply {
                moveTo(x, hipY)
                lineTo(kneeX, kneeY)
                lineTo(footX, footY)
            }
            drawPath(path, SKIN, style = Stroke(width = 6f, cap = StrokeCap.Round))
            if (sit > 0.05f) drawCircle(color = SKIN, radius = 4.5f * sit, center = Offset(kneeX, kneeY))
            // Cipela: pomjerena ka smjeru gledanja u profilu.
            val shoeShift = if (side) dir * 2f else 0f
            drawOval(
                color = SHOE,
                topLeft = Offset(footX - 5f + shoeShift, footY - 2f),
                size = Size(10f, 5f)
            )
        }
    }

    /** Ruka uz rub ekrana dok se lik naslanja. */
    private fun DrawScope.drawLeaningArm(dir: Float, lean: Float, drop: Float) {
        val shoulder = Offset(50f + dir * 20f, 100f + drop)
        val hand = Offset(50f + dir * (20f + 14f * lean), 106f + drop)
        drawLine(color = SKIN, start = shoulder, end = hand, strokeWidth = 6f, cap = StrokeCap.Round)
    }

    private fun DrawScope.drawUpperBody(
        p: AvatarParams,
        expression: AvatarExpression,
        facing: Facing,
        dir: Float
    ) {
        val neck = Offset(50f, 88f)
        val back = facing == Facing.BACK
        val side = facing == Facing.LEFT || facing == Facing.RIGHT

        // Tijelo: lagano diše i njiše se.
        withTransform({
            translate(p.bodyAngleX * 0.3f, 0f)
            scale(1f, 1f + p.breath * 0.015f, pivot = Offset(50f, 120f))
        }) {
            drawRoundRect(
                color = DRESS,
                topLeft = Offset(24f, 94f),
                size = Size(52f, 40f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
            )
            if (!back) {
                drawRoundRect(
                    color = DRESS_LIGHT,
                    topLeft = Offset(38f, 94f),
                    size = Size(24f, 7f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )
            }
            drawRect(color = SKIN_SHADE, topLeft = Offset(44f, 80f), size = Size(12f, 18f))
        }

        // Glava: nagib, pomak i pokret prema pogledu.
        withTransform({
            translate(p.angleX * 0.25f + p.bodyAngleX * 0.3f, p.angleY * 0.2f + p.breath * 0.4f)
            rotate(p.angleZ, pivot = neck)
        }) {
            drawHairBack()
            drawOval(color = SKIN, topLeft = Offset(28f, 27f), size = Size(44f, 50f))
            if (back) {
                // Potiljak: kosa preko cijelog lica, bez očiju i usta.
                drawOval(color = HAIR, topLeft = Offset(25f, 22f), size = Size(50f, 58f))
                drawArc(
                    color = HAIR_LIGHT,
                    startAngle = 200f,
                    sweepAngle = 60f,
                    useCenter = false,
                    topLeft = Offset(32f, 27f),
                    size = Size(36f, 28f),
                    style = Stroke(width = 2f, cap = StrokeCap.Round)
                )
            } else {
            drawHairFront()
            // U profilu se crte lica pomjeraju na stranu i sužavaju.
            withTransform({
                if (side) {
                    translate(dir * 9f, 0f)
                    scale(0.65f, 1f, pivot = Offset(50f, 54f))
                }
            }) {
                drawCheeks(p.cheek)
                drawEye(Offset(40f, 54f), p.eyeLOpen, p.eyeBallX, p.eyeBallY, expression)
                drawEye(Offset(60f, 54f), p.eyeROpen, p.eyeBallX, p.eyeBallY, expression)
                drawBrow(center = 40f, browY = p.browLY, mirror = false)
                drawBrow(center = 60f, browY = p.browRY, mirror = true)
                drawLine(
                    color = SKIN_SHADE,
                    start = Offset(50f, 59f),
                    end = Offset(51f, 62f),
                    strokeWidth = 1f,
                    cap = StrokeCap.Round
                )
                drawMouth(p.mouthOpenY, p.mouthForm)
            }
            }
        }
    }

    private fun DrawScope.drawHairBack() {
        drawOval(color = HAIR_DARK, topLeft = Offset(15f, 14f), size = Size(70f, 78f))
        drawRoundRect(
            color = HAIR_DARK,
            topLeft = Offset(17f, 50f),
            size = Size(16f, 52f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
        )
        drawRoundRect(
            color = HAIR_DARK,
            topLeft = Offset(67f, 50f),
            size = Size(16f, 52f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
        )
    }

    private fun DrawScope.drawHairFront() {
        // Šiške: gornja polovina elipse i tri pramena.
        drawArc(
            color = HAIR,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(25f, 22f),
            size = Size(50f, 42f)
        )
        drawOval(color = HAIR, topLeft = Offset(28f, 38f), size = Size(15f, 15f))
        drawOval(color = HAIR, topLeft = Offset(42f, 40f), size = Size(16f, 14f))
        drawOval(color = HAIR, topLeft = Offset(57f, 38f), size = Size(15f, 15f))
        drawArc(
            color = HAIR_LIGHT,
            startAngle = 200f,
            sweepAngle = 60f,
            useCenter = false,
            topLeft = Offset(32f, 27f),
            size = Size(36f, 28f),
            style = Stroke(width = 2f, cap = StrokeCap.Round)
        )
        // Pramenovi sa strane lica.
        drawRoundRect(
            color = HAIR,
            topLeft = Offset(24f, 40f),
            size = Size(7f, 36f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.5f, 3.5f)
        )
        drawRoundRect(
            color = HAIR,
            topLeft = Offset(69f, 40f),
            size = Size(7f, 36f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.5f, 3.5f)
        )
    }

    private fun DrawScope.drawCheeks(amount: Float) {
        val a = (amount * 0.6f).coerceIn(0f, 1f)
        drawCircle(color = BLUSH.copy(alpha = a), radius = 5f, center = Offset(35f, 63f))
        drawCircle(color = BLUSH.copy(alpha = a), radius = 5f, center = Offset(65f, 63f))
    }

    private fun DrawScope.drawEye(
        center: Offset,
        open: Float,
        ballX: Float,
        ballY: Float,
        expression: AvatarExpression
    ) {
        val wide = if (expression == AvatarExpression.SURPRISED) 1.2f else 1f
        val rx = 6.5f
        val ry = 7f * open.coerceIn(0f, 1f) * wide
        if (open < 0.12f) {
            // Zatvoreno oko: zakrivljena crta trepavica.
            val lid = Path().apply {
                moveTo(center.x - rx, center.y + 1f)
                quadraticBezierTo(center.x, center.y + 4f, center.x + rx, center.y + 1f)
            }
            drawPath(lid, EYE_LINE, style = Stroke(width = 1.6f, cap = StrokeCap.Round))
            return
        }
        val eyePath = Path().apply {
            addOval(Rect(center.x - rx, center.y - ry, center.x + rx, center.y + ry))
        }
        drawPath(eyePath, EYE_WHITE, style = Fill)
        clipPath(eyePath) {
            val ix = center.x + ballX * 2.2f
            val iy = center.y - ballY * 1.6f
            drawCircle(color = IRIS, radius = 4.6f, center = Offset(ix, iy))
            drawCircle(color = IRIS_DARK, radius = 2.4f, center = Offset(ix, iy))
            drawCircle(color = EYE_WHITE, radius = 1.2f, center = Offset(ix - 1.4f, iy - 1.6f))
        }
        drawArc(
            color = EYE_LINE,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(center.x - rx, center.y - ry),
            size = Size(rx * 2f, ry * 2f),
            style = Stroke(width = 1.6f, cap = StrokeCap.Round)
        )
    }

    private fun DrawScope.drawBrow(center: Float, browY: Float, mirror: Boolean) {
        val y0 = 43f - browY * 3.5f
        // Spuštena obrva (ljutnja): unutrašnji kraj niže; podignuta: blagi luk.
        val inner = if (browY < 0f) -browY * 3f else -browY * 0.8f
        val dir = if (mirror) -1f else 1f
        val outer = Offset(center - dir * 7f, y0)
        val innerEnd = Offset(center + dir * 7f, y0 + inner)
        drawLine(
            color = BROW,
            start = outer,
            end = innerEnd,
            strokeWidth = 1.9f,
            cap = StrokeCap.Round
        )
    }

    private fun DrawScope.drawMouth(open: Float, form: Float) {
        val o = open.coerceIn(0f, 1f) * 6f
        val halfWidth = 8f + form * 1.5f
        val l = 50f - halfWidth
        val r = 50f + halfWidth
        val y = 69f
        val lipY = y + form * 3f
        if (o < 0.6f) {
            val line = Path().apply {
                moveTo(l, y)
                quadraticBezierTo(50f, lipY + 1f, r, y)
            }
            drawPath(line, LIP, style = Stroke(width = 1.6f, cap = StrokeCap.Round))
        } else {
            val shape = Path().apply {
                moveTo(l, y)
                quadraticBezierTo(50f, lipY, r, y)
                quadraticBezierTo(50f, lipY + 2f * o, l, y)
                close()
            }
            drawPath(shape, MOUTH_INSIDE, style = Fill)
            if (o > 2.5f) {
                drawOval(
                    color = TONGUE,
                    topLeft = Offset(50f - 3.5f, y + o * 0.45f),
                    size = Size(7f, o * 0.45f)
                )
            }
            drawPath(shape, LIP, style = Stroke(width = 1.3f, cap = StrokeCap.Round))
        }
    }

    private companion object {
        const val DESIGN_W = 100f
        const val DESIGN_H = 120f
        /** Smanjenje lika da stane i s nogama. */
        const val FIGURE_SCALE = 0.78f
        /** Visina na kojoj stoje stopala i tačka gdje počinju noge (u prostoru lika prije smanjenja). */
        const val FOOT_Y = 150f
        const val LEG_TOP_Y = 132f
        /** Za koliko se tijelo spusti kad lik sjedne. */
        const val SIT_DROP = 10f
        const val LEAN_DEGREES = 10f

        val SKIN = Color(0xFFFFE3DA)
        val SKIN_SHADE = Color(0xFFF2C4BC)
        val HAIR = Color(0xFF9B59C9)
        val HAIR_DARK = Color(0xFF7A3FA6)
        val HAIR_LIGHT = Color(0xFFC79BE6)
        val DRESS = Color(0xFFE0457B)
        val DRESS_LIGHT = Color(0xFFFF8FB1)
        val BLUSH = Color(0xFFFF7FA8)
        val BROW = Color(0xFF5E2F80)
        val EYE_WHITE = Color(0xFFFFFFFF)
        val EYE_LINE = Color(0xFF4A2260)
        val IRIS = Color(0xFFB66AD9)
        val IRIS_DARK = Color(0xFF3B1A50)
        val LIP = Color(0xFFB5476B)
        val MOUTH_INSIDE = Color(0xFF8A2D4D)
        val TONGUE = Color(0xFFFF7FA8)
        val SHOE = Color(0xFF5E2F80)
    }
}
