package io.androllm.feature.avatar

import androidx.compose.runtime.Immutable
import io.androllm.core.soul.AvatarPose
import io.androllm.core.soul.AvatarWalkCycle
import io.androllm.core.soul.ClipTimings
import io.androllm.core.soul.Facing
import io.androllm.core.soul.MotionState
import io.androllm.core.soul.MotionStep

/**
 * Podešavanje pokreta lika. Podrazumijevana trajanja i brzina hoda dolaze iz [ClipTimings];
 * kad stigne pravi model, ovdje se zamijene stvarnim trajanjima klipova.
 */
@Immutable
data class AvatarMotionConfig(
    val timings: ClipTimings = ClipTimings(),
    /** Dužina jednog ciklusa koraka kao dio širine lika (dva koraka). */
    val strideWidthFactor: Float = 0.6f,
)

/** Vrsta koraka koji se trenutno izvodi (renderer po tome bira animaciju). */
enum class MotionKind { IDLE, STAND_UP, TURN, WALK, SIT_DOWN, LEAN, PAUSE }

/**
 * Stanje pokreta koje renderer dobija svaki frejm.
 * [walkPhase] je ciklus koraka 0..1, [stepProgress] je napredak trenutnog koraka 0..1.
 */
@Immutable
data class AvatarMotionParams(
    val pose: AvatarPose = AvatarPose.STANDING,
    val facing: Facing = Facing.FRONT,
    val walkPhase: Float = 0f,
    val stepProgress: Float = 0f,
    val kind: MotionKind = MotionKind.IDLE,
) {
    private val seated: Boolean get() = pose == AvatarPose.SITTING || pose == AvatarPose.SLEEPING

    /** Koliko je tijelo spušteno: 0 uspravno, 1 sjedi. Interpolira se tokom ustajanja i sjedanja. */
    val sitAmount: Float
        get() = when (kind) {
            MotionKind.SIT_DOWN -> stepProgress
            MotionKind.STAND_UP -> if (seated) 1f - stepProgress else 0f
            else -> if (seated) 1f else 0f
        }.coerceIn(0f, 1f)

    /** Koliko je lik nagnut prema rubu: 0 uspravno, 1 potpuno naslonjen. */
    val leanAmount: Float
        get() = when (kind) {
            MotionKind.LEAN -> stepProgress
            MotionKind.STAND_UP -> if (pose == AvatarPose.LEANING) 1f - stepProgress else 0f
            else -> if (pose == AvatarPose.LEANING) 1f else 0f
        }.coerceIn(0f, 1f)

    val isWalking: Boolean get() = kind == MotionKind.WALK

    companion object {
        val Idle = AvatarMotionParams()

        fun at(pose: AvatarPose, facing: Facing) = AvatarMotionParams(pose = pose, facing = facing)

        /** Iz stanja plana: [stridePx] je put koji odgovara jednom ciklusu koraka. */
        fun from(state: MotionState, stridePx: Float): AvatarMotionParams {
            val step = state.step
            val kind = when (step) {
                null -> MotionKind.IDLE
                is MotionStep.StandUp -> MotionKind.STAND_UP
                is MotionStep.Turn -> MotionKind.TURN
                is MotionStep.Walk -> MotionKind.WALK
                is MotionStep.SitDown -> MotionKind.SIT_DOWN
                is MotionStep.Lean -> MotionKind.LEAN
                is MotionStep.Pause -> MotionKind.PAUSE
            }
            val phase = if (step is MotionStep.Walk) AvatarWalkCycle.phase(step, state.progress, stridePx) else 0f
            return AvatarMotionParams(
                pose = state.pose,
                facing = state.facing,
                walkPhase = phase,
                stepProgress = if (step == null) 0f else state.progress,
                kind = kind,
            )
        }
    }
}
