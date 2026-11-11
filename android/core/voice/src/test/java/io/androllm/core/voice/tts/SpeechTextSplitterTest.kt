package io.androllm.core.voice.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextSplitterTest {

    @Test
    fun `dijeli na rečenice po tački, uzviku i upitniku`() {
        val parts = SpeechTextSplitter.splitSentences("Zdravo. Kako si? Odlično!")
        assertEquals(listOf("Zdravo.", "Kako si?", "Odlično!"), parts)
    }

    @Test
    fun `novi red dijeli rečenice`() {
        val parts = SpeechTextSplitter.splitSentences("Prva linija\nDruga linija")
        assertEquals(listOf("Prva linija", "Druga linija"), parts)
    }

    @Test
    fun `decimalni broj se ne dijeli`() {
        val parts = SpeechTextSplitter.splitSentences("Pi je 3.14 otprilike.")
        assertEquals(listOf("Pi je 3.14 otprilike."), parts)
    }

    @Test
    fun `markdown znakovi se uklanjaju`() {
        val cleaned = SpeechTextSplitter.clean("## Naslov\n**podebljano** i `kod`")
        assertEquals("Naslov\npodebljano i kod", cleaned)
    }

    @Test
    fun `markdown veza zadržava samo tekst`() {
        assertEquals("pogledaj ovdje", SpeechTextSplitter.clean("pogledaj [ovdje](https://primjer.ba)"))
    }

    @Test
    fun `emoji se uklanjaju`() {
        val parts = SpeechTextSplitter.splitSentences("Super 😀 posao ✨!")
        assertEquals(listOf("Super posao !"), parts)
    }

    @Test
    fun `komadi bez slova i cifara se odbacuju`() {
        assertTrue(SpeechTextSplitter.splitSentences("*** ... ---").isEmpty())
        assertTrue(SpeechTextSplitter.splitSentences("   \n  ").isEmpty())
    }

    @Test
    fun `lista sa crticama gubi oznaku`() {
        val parts = SpeechTextSplitter.splitSentences("- jedan\n- dva\n1. tri")
        assertEquals(listOf("jedan", "dva", "tri"), parts)
    }

    @Test
    fun `dugačka rečenica se reže na dozvoljenu dužinu`() {
        val long = List(200) { "riječ" }.joinToString(" ")
        val parts = SpeechTextSplitter.splitSentences(long)
        assertTrue(parts.size > 1)
        assertTrue(parts.all { it.length <= SpeechTextSplitter.MAX_CHUNK_CHARS })
    }
}
