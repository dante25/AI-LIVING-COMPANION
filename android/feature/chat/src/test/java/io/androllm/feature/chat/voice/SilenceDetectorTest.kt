package io.androllm.feature.chat.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SilenceDetectorTest {

    private fun run(rmsList: List<Float>, chunkMs: Long = 200L): SilenceDetector.State =
        rmsList.fold(SilenceDetector.State()) { s, r -> SilenceDetector.advance(s, r, chunkMs) }

    @Test
    fun silence_before_speech_never_stops() {
        val s = run(List(40) { 0.0f })
        assertNull(SilenceDetector.stopReason(s))
    }

    @Test
    fun speech_then_two_seconds_silence_stops() {
        val s = run(listOf(0.1f, 0.1f) + List(10) { 0.0f })
        assertEquals(SilenceDetector.StopReason.SILENCE, SilenceDetector.stopReason(s))
    }

    @Test
    fun short_pause_does_not_stop() {
        val s = run(listOf(0.1f) + List(5) { 0.0f })
        assertNull(SilenceDetector.stopReason(s))
    }

    @Test
    fun speech_resets_silence_counter() {
        val s = run(listOf(0.1f) + List(9) { 0.0f } + listOf(0.1f) + List(5) { 0.0f })
        assertNull(SilenceDetector.stopReason(s))
    }

    @Test
    fun max_duration_stops_even_without_speech() {
        val s = run(List(150) { 0.0f })
        assertEquals(SilenceDetector.StopReason.MAX_DURATION, SilenceDetector.stopReason(s))
    }

    @Test
    fun rms_of_empty_is_zero_and_of_constant_matches() {
        assertEquals(0f, SilenceDetector.rms(FloatArray(0)), 0f)
        assertEquals(0.5f, SilenceDetector.rms(FloatArray(10) { 0.5f }), 0.0001f)
        assertTrue(SilenceDetector.rms(FloatArray(10) { 0.5f }) > SilenceDetector.SPEECH_RMS_THRESHOLD)
    }
}
