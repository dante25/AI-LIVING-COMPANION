package io.androllm.core.voice.tts

/**
 * Čista logika (bez Android klasa) za čitanje WAV fajla sa PCM 16-bit podacima.
 */
object WavPcm {

    /** Rezultat dekodiranja: mono float uzorci u rasponu -1..1 i frekvencija uzorkovanja. */
    class Decoded(val sampleRate: Int, val samples: FloatArray)

    /**
     * Parsira WAV bajtove. Vraća null kada zaglavlje nije valjano ili format
     * nije PCM 16-bit. Višekanalni zvuk se svodi na mono (prosjek kanala).
     */
    fun parse(bytes: ByteArray): Decoded? {
        if (bytes.size < 12) return null
        if (!tagAt(bytes, 0, "RIFF") || !tagAt(bytes, 8, "WAVE")) return null

        var pos = 12
        var sampleRate = 0
        var channels = 0
        var bits = 0
        var format = 0
        var haveFmt = false

        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.ISO_8859_1)
            val declared = u32(bytes, pos + 4)
            val bodyStart = pos + 8
            val available = (bytes.size - bodyStart).toLong()
            when (id) {
                "fmt " -> {
                    if (declared < 16 || available < 16) return null
                    format = u16(bytes, bodyStart)
                    channels = u16(bytes, bodyStart + 2)
                    sampleRate = u32(bytes, bodyStart + 4).toInt()
                    bits = u16(bytes, bodyStart + 14)
                    haveFmt = true
                }
                "data" -> {
                    if (!haveFmt) return null
                    // PCM (1) ili WAVE_FORMAT_EXTENSIBLE (0xFFFE) sa 16 bita.
                    if ((format != 1 && format != 0xFFFE) || bits != 16) return null
                    if (channels < 1 || sampleRate <= 0) return null
                    // Streaming enkoderi znaju upisati 0 ili 0xFFFFFFFF kao veličinu.
                    val size = if (declared == 0L || declared > available) available else declared
                    val frameBytes = 2 * channels
                    val usable = (size / frameBytes) * frameBytes
                    val samples = pcm16ToFloat(bytes, bodyStart, usable.toInt(), channels)
                    return Decoded(sampleRate, samples)
                }
            }
            val next = bodyStart.toLong() + declared + (declared and 1L)
            if (next > bytes.size) return null
            pos = next.toInt()
        }
        return null
    }

    /**
     * Pretvara little-endian PCM 16-bit u float -1..1. Za [channels] > 1
     * uzima prosjek kanala po okviru.
     */
    fun pcm16ToFloat(bytes: ByteArray, offset: Int, length: Int, channels: Int = 1): FloatArray {
        val ch = channels.coerceAtLeast(1)
        val frames = length / (2 * ch)
        val out = FloatArray(frames)
        var p = offset
        for (i in 0 until frames) {
            var sum = 0
            for (c in 0 until ch) {
                val lo = bytes[p].toInt() and 0xFF
                val hi = bytes[p + 1].toInt() // predznak ostaje
                sum += (hi shl 8) or lo
                p += 2
            }
            out[i] = (sum.toFloat() / ch / 32768f).coerceIn(-1f, 1f)
        }
        return out
    }

    private fun tagAt(b: ByteArray, at: Int, tag: String): Boolean {
        if (at + 4 > b.size) return false
        for (i in 0 until 4) if (b[at + i] != tag[i].code.toByte()) return false
        return true
    }

    private fun u16(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, at: Int): Long =
        (u16(b, at).toLong()) or (u16(b, at + 2).toLong() shl 16)
}
