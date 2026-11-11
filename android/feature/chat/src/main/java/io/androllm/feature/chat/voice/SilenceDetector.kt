package io.androllm.feature.chat.voice

import kotlin.math.sqrt

/**
 * Pure (Android-free) auto-stop logic for push-to-talk recording: stop after
 * [SILENCE_STOP_MS] of silence once speech was heard, or after
 * [MAX_RECORDING_MS] in total.
 */
object SilenceDetector {

    /** RMS (0..1) above which a chunk counts as speech. */
    const val SPEECH_RMS_THRESHOLD = 0.015f
    const val SILENCE_STOP_MS = 2000L
    const val MAX_RECORDING_MS = 30_000L

    enum class StopReason { SILENCE, MAX_DURATION }

    data class State(
        val elapsedMs: Long = 0L,
        val speechDetected: Boolean = false,
        val silentMs: Long = 0L
    )

    fun rms(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (s in samples) sum += (s * s).toDouble()
        return sqrt(sum / samples.size).toFloat()
    }

    /** Folds one chunk (its [rms] and duration [chunkMs]) into [state]. */
    fun advance(state: State, rms: Float, chunkMs: Long): State {
        val elapsed = state.elapsedMs + chunkMs
        return if (rms >= SPEECH_RMS_THRESHOLD) {
            State(elapsedMs = elapsed, speechDetected = true, silentMs = 0L)
        } else {
            State(
                elapsedMs = elapsed,
                speechDetected = state.speechDetected,
                silentMs = if (state.speechDetected) state.silentMs + chunkMs else 0L
            )
        }
    }

    /** Non-null when recording should stop now. */
    fun stopReason(state: State): StopReason? = when {
        state.elapsedMs >= MAX_RECORDING_MS -> StopReason.MAX_DURATION
        state.speechDetected && state.silentMs >= SILENCE_STOP_MS -> StopReason.SILENCE
        else -> null
    }
}
