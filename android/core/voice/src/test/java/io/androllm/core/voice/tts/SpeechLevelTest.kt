package io.androllm.core.voice.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechLevelTest {

    @Test
    fun `tisina daje nivo nula`() {
        assertEquals(0f, SpeechLevel.level(FloatArray(480)), 0f)
        assertEquals(0f, SpeechLevel.level(FloatArray(0)), 0f)
    }

    @Test
    fun `glasan zvuk je ogranicen na jedan`() {
        val loud = FloatArray(480) { 0.9f }
        assertEquals(1f, SpeechLevel.level(loud), 0f)
    }

    @Test
    fun `tiši zvuk daje manji nivo od glasnijeg`() {
        val quiet = FloatArray(480) { 0.02f }
        val medium = FloatArray(480) { 0.1f }
        assertTrue(SpeechLevel.level(quiet) < SpeechLevel.level(medium))
    }

    @Test
    fun `prozori od 30 ms`() {
        // 16 kHz: 30 ms = 480 uzoraka; 1000 uzoraka = 3 prozora (480, 480, 40).
        val samples = FloatArray(1000) { if (it < 480) 0f else 0.5f }
        val levels = SpeechLevel.windowLevels(samples, 16_000)
        assertEquals(3, levels.size)
        assertEquals(0f, levels[0], 0f)
        assertTrue(levels[1] > 0.9f)
    }

    @Test
    fun `levelAt vraca nivo prozora i nulu izvan zvuka`() {
        val levels = floatArrayOf(0.1f, 0.5f, 0.9f)
        assertEquals(0.1f, SpeechLevel.levelAt(levels, 0), 0f)
        assertEquals(0.5f, SpeechLevel.levelAt(levels, 45), 0f)
        assertEquals(0.9f, SpeechLevel.levelAt(levels, 89), 0f)
        assertEquals(0f, SpeechLevel.levelAt(levels, 90), 0f)
        assertEquals(0f, SpeechLevel.levelAt(levels, -5), 0f)
    }
}
