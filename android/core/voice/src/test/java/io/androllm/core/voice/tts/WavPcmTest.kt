package io.androllm.core.voice.tts

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WavPcmTest {

    private fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
    private fun le32(v: Int) = le16(v and 0xFFFF) + le16((v shr 16) and 0xFFFF)

    private fun wav(
        samples: ShortArray,
        sampleRate: Int = 22050,
        channels: Int = 1,
        bits: Int = 16,
        format: Int = 1,
        dataSizeOverride: Int? = null,
        extraChunk: Boolean = false
    ): ByteArray {
        val data = ByteArrayOutputStream()
        samples.forEach { data.write(le16(it.toInt())) }
        val pcm = data.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray())
        out.write(le32(36 + pcm.size))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(le32(16))
        out.write(le16(format))
        out.write(le16(channels))
        out.write(le32(sampleRate))
        out.write(le32(sampleRate * channels * bits / 8))
        out.write(le16(channels * bits / 8))
        out.write(le16(bits))
        if (extraChunk) {
            out.write("LIST".toByteArray())
            out.write(le32(3))
            out.write(byteArrayOf(1, 2, 3, 0)) // 3 bajta + 1 bajt dopune
        }
        out.write("data".toByteArray())
        out.write(le32(dataSizeOverride ?: pcm.size))
        out.write(pcm)
        return out.toByteArray()
    }

    @Test
    fun parsesSampleRateAndSamples() {
        val d = WavPcm.parse(wav(shortArrayOf(0, 16384, -16384), sampleRate = 24000))
        assertNotNull(d)
        assertEquals(24000, d!!.sampleRate)
        assertEquals(3, d.samples.size)
        assertEquals(0f, d.samples[0], 1e-6f)
        assertEquals(0.5f, d.samples[1], 1e-6f)
        assertEquals(-0.5f, d.samples[2], 1e-6f)
    }

    @Test
    fun extremesStayInRange() {
        val d = WavPcm.parse(wav(shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE)))!!
        assertEquals(-1f, d.samples[1], 1e-6f)
        assert(d.samples[0] <= 1f && d.samples[0] > 0.999f)
    }

    @Test
    fun skipsUnknownChunksWithPadding() {
        val d = WavPcm.parse(wav(shortArrayOf(1000, 2000), extraChunk = true))
        assertNotNull(d)
        assertEquals(2, d!!.samples.size)
    }

    @Test
    fun streamingDataSizeZeroUsesRemainingBytes() {
        val d = WavPcm.parse(wav(shortArrayOf(1, 2, 3, 4), dataSizeOverride = 0))
        assertEquals(4, d!!.samples.size)
    }

    @Test
    fun oversizedDataSizeIsClamped() {
        val d = WavPcm.parse(wav(shortArrayOf(1, 2, 3), dataSizeOverride = -1))
        assertEquals(3, d!!.samples.size)
    }

    @Test
    fun stereoIsAveragedToMono() {
        val d = WavPcm.parse(wav(shortArrayOf(16384, -16384, 16384, 16384), channels = 2))!!
        assertEquals(2, d.samples.size)
        assertEquals(0f, d.samples[0], 1e-6f)
        assertEquals(0.5f, d.samples[1], 1e-6f)
    }

    @Test
    fun rejectsBadHeaderAndUnsupportedFormats() {
        assertNull(WavPcm.parse(ByteArray(0)))
        assertNull(WavPcm.parse("not a wav file at all".toByteArray()))
        assertNull(WavPcm.parse(wav(shortArrayOf(1), format = 3)))
        assertNull(WavPcm.parse(wav(shortArrayOf(1), bits = 8)))
    }

    @Test
    fun emptyDataGivesEmptySamples() {
        val d = WavPcm.parse(wav(shortArrayOf()))!!
        assertEquals(0, d.samples.size)
    }

    @Test
    fun pcm16ToFloatHandlesNegativeBytes() {
        // 0xFF 0xFF = -1, 0x00 0x80 = -32768
        val out = WavPcm.pcm16ToFloat(byteArrayOf(-1, -1, 0, -128), 0, 4)
        assertEquals(-1f / 32768f, out[0], 1e-9f)
        assertEquals(-1f, out[1], 1e-9f)
    }
}
