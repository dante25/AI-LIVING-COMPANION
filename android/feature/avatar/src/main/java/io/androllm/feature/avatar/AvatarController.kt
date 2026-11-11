package io.androllm.feature.avatar

import io.androllm.core.soul.SoulAction
import io.androllm.core.soul.SoulState
import io.androllm.core.voice.tts.SpeechLevel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Čiste funkcije za izbor izraza i pokrete usta. */
object AvatarLogic {

    /** Osnovni izraz iz stanja duše i zadnje odluke (bez povremenog PLAYFUL, to radi kontroler). */
    fun chooseExpression(state: SoulState, action: SoulAction): AvatarExpression = when {
        state.anger >= 60.0 || action == SoulAction.SULK -> AvatarExpression.ANGRY
        action == SoulAction.SLEEP || state.energy < 20.0 -> AvatarExpression.SLEEPY
        state.boredom >= 60.0 || action == SoulAction.SEEK_ATTENTION -> AvatarExpression.BORED
        action == SoulAction.REFUSE -> AvatarExpression.PLAYFUL
        state.mood >= 75.0 -> AvatarExpression.HAPPY
        else -> AvatarExpression.NEUTRAL
    }

    /**
     * Glađenje otvorenosti usta: brzo otvaranje (attack), sporije zatvaranje (release).
     * [dtMs] je vrijeme od prošlog koraka.
     */
    fun smoothMouth(previous: Float, target: Float, dtMs: Long): Float {
        val t = target.coerceIn(0f, 1f)
        val tau = if (t > previous) MOUTH_ATTACK_MS else MOUTH_RELEASE_MS
        val alpha = 1f - exp(-dtMs.coerceAtLeast(0L).toFloat() / tau)
        return (previous + (t - previous) * alpha).coerceIn(0f, 1f)
    }

    /** Otvorenost usta iz jednog prozora PCM uzoraka (mono, -1..1) uz glađenje. */
    fun mouthOpenFromPcm(pcm: FloatArray, previous: Float, dtMs: Long = 30L): Float =
        smoothMouth(previous, SpeechLevel.level(pcm), dtMs)

    private const val MOUTH_ATTACK_MS = 40f
    private const val MOUTH_RELEASE_MS = 110f
}

/**
 * Pokreće živost lika: bira izraz iz stanja duše, trepće svakih 3–6 s, diše,
 * lagano prati pogledom i miče usta prema glasnoći govora.
 *
 * Nema Androida: vrijeme ulazi kroz [step] (u milisekundama), a slučajnost kroz [random],
 * pa je ponašanje determinističko i testabilno. Poziva se iz jedne niti/korutine.
 */
class AvatarController(
    private val random: Random = Random(7),
    startMs: Long = 0L,
) {
    private val _params = MutableStateFlow(AvatarParams())
    val params: StateFlow<AvatarParams> = _params.asStateFlow()

    private val _expression = MutableStateFlow(AvatarExpression.NEUTRAL)
    val expression: StateFlow<AvatarExpression> = _expression.asStateFlow()

    private var soul = SoulState()
    private var action = SoulAction.RESPOND
    private var speechLevel = 0f

    private var overrideExpression: AvatarExpression? = null
    private var overrideUntilMs = 0L

    /** Trenutak sljedećeg treptaja (ms). */
    var nextBlinkAtMs: Long = startMs + randomBlinkDelay()
        private set
    private var blinkStartMs = -1L

    private var nextPlayfulAtMs = startMs + random.nextLong(20_000L, 40_001L)
    private var playfulUntilMs = 0L

    private var nextGazeAtMs = startMs + random.nextLong(1_500L, 4_001L)
    private var gazeTargetX = 0f
    private var gazeTargetY = 0f
    private var gazeOverrideX: Float? = null
    private var gazeOverrideY: Float? = null
    private var gazeX = 0f
    private var gazeY = 0f
    private var headX = 0f
    private var headY = 0f

    private var eyeBase = 1f
    private var browL = 0f
    private var browR = 0f
    private var form = 0.2f
    private var cheek = 0.15f
    private var tilt = 0f
    private var mouthMin = 0f
    private var mouth = 0f

    private var lastMs = startMs

    /** Novo stanje duše i zadnja odluka o ponašanju. */
    fun onSoul(state: SoulState, action: SoulAction) {
        soul = state
        this.action = action
    }

    /** Glasnoća govora 0..1 (iz ReadAloudSpeaker.speechLevel). */
    fun onSpeechLevel(level: Float) {
        speechLevel = level.coerceIn(0f, 1f)
    }

    /** Kratkotrajan izraz (npr. SURPRISED ili CONFUSED) koji pregazi izbor iz stanja. */
    fun react(expression: AvatarExpression, nowMs: Long, durationMs: Long = 2_000L) {
        overrideExpression = expression
        overrideUntilMs = nowMs + durationMs
    }

    /** Pogled prema tački (-1..1); null vraća slobodno lutanje pogleda. */
    fun lookAt(x: Float?, y: Float?) {
        gazeOverrideX = x?.coerceIn(-1f, 1f)
        gazeOverrideY = y?.coerceIn(-1f, 1f)
    }

    /** Jedan korak animacije u trenutku [nowMs]; vraća i emituje nove parametre. */
    fun step(nowMs: Long): AvatarParams {
        val dt = (nowMs - lastMs).coerceIn(0L, 250L)
        lastMs = nowMs

        // Povremeno razigran izraz kad je inače sve neutralno.
        if (nowMs >= nextPlayfulAtMs) {
            playfulUntilMs = nowMs + 4_000L
            nextPlayfulAtMs = nowMs + random.nextLong(20_000L, 40_001L)
        }
        val expr = pickExpression(nowMs)
        _expression.value = expr
        val pose = poseFor(expr)

        val a = smoothingAlpha(dt, POSE_TAU_MS)
        eyeBase += (pose.eyeOpen - eyeBase) * a
        browL += (pose.browL - browL) * a
        browR += (pose.browR - browR) * a
        form += (pose.mouthForm - form) * a
        cheek += (pose.cheek - cheek) * a
        tilt += (pose.tilt - tilt) * a
        mouthMin += (pose.mouthMin - mouthMin) * a

        // Treptanje: 3-6 s razmaka, treptaj traje BLINK_MS.
        if (blinkStartMs < 0 && nowMs >= nextBlinkAtMs) {
            blinkStartMs = nowMs
            nextBlinkAtMs = nowMs + randomBlinkDelay()
        }
        var blinkFactor = 1f
        if (blinkStartMs >= 0) {
            val t = (nowMs - blinkStartMs).toFloat() / BLINK_MS
            if (t >= 1f) blinkStartMs = -1L else blinkFactor = abs(2f * t - 1f)
        }

        // Pogled: povremeno nova meta, glatko praćenje, glava prati sporije.
        if (nowMs >= nextGazeAtMs) {
            gazeTargetX = random.nextFloat() * 0.8f - 0.4f
            gazeTargetY = random.nextFloat() * 0.5f - 0.25f
            nextGazeAtMs = nowMs + random.nextLong(1_500L, 4_001L)
        }
        val tx = gazeOverrideX ?: gazeTargetX
        val ty = gazeOverrideY ?: gazeTargetY
        val ga = smoothingAlpha(dt, GAZE_TAU_MS)
        gazeX += (tx - gazeX) * ga
        gazeY += (ty - gazeY) * ga
        val ha = smoothingAlpha(dt, HEAD_TAU_MS)
        headX += (gazeX * 10f - headX) * ha
        headY += (gazeY * 8f - headY) * ha

        // Usta: iz glasnoće govora, ali ne ispod izraza (npr. iznenađenje).
        mouth = AvatarLogic.smoothMouth(mouth, max(speechLevel, mouthMin), dt)

        val phase = (nowMs % BREATH_PERIOD_MS).toFloat() / BREATH_PERIOD_MS
        val breath = 0.5f + 0.5f * sin(2.0 * PI * phase).toFloat()
        val body = 2f * sin(2.0 * PI * (nowMs % BODY_PERIOD_MS).toFloat() / BODY_PERIOD_MS).toFloat()

        val eye = (eyeBase * blinkFactor).coerceIn(0f, 1f)
        val out = AvatarParams(
            angleX = headX,
            angleY = headY,
            angleZ = tilt,
            eyeLOpen = eye,
            eyeROpen = eye,
            eyeBallX = gazeX,
            eyeBallY = gazeY,
            browLY = browL,
            browRY = browR,
            mouthOpenY = mouth,
            mouthForm = form.coerceIn(-1f, 1f),
            bodyAngleX = body,
            breath = breath,
            cheek = cheek.coerceIn(0f, 1f),
        )
        _params.value = out
        return out
    }

    private fun pickExpression(nowMs: Long): AvatarExpression {
        val forced = overrideExpression
        if (forced != null) {
            if (nowMs < overrideUntilMs) return forced
            overrideExpression = null
        }
        val base = AvatarLogic.chooseExpression(soul, action)
        return if (base == AvatarExpression.NEUTRAL && nowMs < playfulUntilMs) AvatarExpression.PLAYFUL else base
    }

    private fun randomBlinkDelay(): Long = random.nextLong(3_000L, 6_001L)

    private fun smoothingAlpha(dtMs: Long, tauMs: Float): Float = 1f - exp(-dtMs.toFloat() / tauMs)

    private class Pose(
        val eyeOpen: Float,
        val browL: Float,
        val browR: Float,
        val mouthForm: Float,
        val cheek: Float,
        val tilt: Float,
        val mouthMin: Float = 0f,
    )

    private fun poseFor(e: AvatarExpression): Pose = when (e) {
        AvatarExpression.NEUTRAL -> Pose(1f, 0f, 0f, 0.2f, 0.15f, 0f)
        AvatarExpression.HAPPY -> Pose(0.9f, 0.3f, 0.3f, 0.9f, 0.7f, 3f)
        AvatarExpression.PLAYFUL -> Pose(1f, 0.55f, 0f, 0.7f, 0.5f, 8f)
        AvatarExpression.ANGRY -> Pose(0.75f, -0.8f, -0.8f, -0.7f, 0.1f, 0f)
        AvatarExpression.BORED -> Pose(0.55f, -0.1f, -0.1f, -0.2f, 0f, -6f)
        AvatarExpression.SLEEPY -> Pose(0.15f, -0.2f, -0.2f, 0f, 0.2f, -4f)
        AvatarExpression.CONFUSED -> Pose(1f, 0.6f, -0.3f, -0.3f, 0.1f, 6f)
        AvatarExpression.SURPRISED -> Pose(1f, 1f, 1f, 0f, 0.2f, 0f, mouthMin = 0.45f)
    }

    private companion object {
        const val BLINK_MS = 180f
        const val POSE_TAU_MS = 180f
        const val GAZE_TAU_MS = 220f
        const val HEAD_TAU_MS = 600f
        const val BREATH_PERIOD_MS = 4_000L
        const val BODY_PERIOD_MS = 7_000L
    }
}
