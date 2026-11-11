package io.androllm.core.voice.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsVoiceRankingTest {

    private fun v(name: String, lang: String, net: Boolean = false, q: Int = 400) =
        TtsVoiceInfo(name = name, languageTag = lang, language = TtsVoiceRanking.normalizeLanguage(lang), networkRequired = net, quality = q)

    @Test
    fun normalizesLanguageCodes() {
        assertEquals("hr", TtsVoiceRanking.normalizeLanguage("hrv"))
        assertEquals("sr", TtsVoiceRanking.normalizeLanguage("sr-Latn-RS"))
        assertEquals("bs", TtsVoiceRanking.normalizeLanguage("bs_BA"))
        assertEquals("en", TtsVoiceRanking.normalizeLanguage("EN"))
    }

    @Test
    fun sortPutsHrSrBsThenEnFirst() {
        val sorted = TtsVoiceRanking.sort(
            listOf(v("de1", "de-DE"), v("en1", "en-US"), v("bs1", "bs-BA"), v("sr1", "sr-RS"), v("hr1", "hr-HR"))
        ).map { it.name }
        assertEquals(listOf("hr1", "sr1", "bs1", "en1", "de1"), sorted)
    }

    @Test
    fun sortPutsOfflineAndHigherQualityFirstWithinLanguage() {
        val sorted = TtsVoiceRanking.sort(
            listOf(
                v("net-hq", "hr-HR", net = true, q = 500),
                v("off-low", "hr-HR", net = false, q = 200),
                v("off-hq", "hr-HR", net = false, q = 500)
            )
        ).map { it.name }
        assertEquals(listOf("off-hq", "off-low", "net-hq"), sorted)
    }

    @Test
    fun pickBestHonoursExplicitName() {
        val voices = listOf(v("hr1", "hr-HR"), v("en1", "en-US"))
        assertEquals("en1", TtsVoiceRanking.pickBest(voices, "en1")?.name)
    }

    @Test
    fun pickBestIgnoresUnknownPreferredName() {
        val voices = listOf(v("hr1", "hr-HR"), v("en1", "en-US"))
        assertEquals("hr1", TtsVoiceRanking.pickBest(voices, "ne-postoji")?.name)
    }

    @Test
    fun pickBestPrefersBosnianThenCroatianThenSerbian() {
        val voices = listOf(v("sr1", "sr-RS"), v("hr1", "hr-HR"), v("bs1", "bs-BA"), v("en1", "en-US"))
        assertEquals("bs1", TtsVoiceRanking.pickBest(voices)?.name)
        assertEquals("hr1", TtsVoiceRanking.pickBest(voices.filter { it.name != "bs1" })?.name)
        assertEquals("sr1", TtsVoiceRanking.pickBest(voices.filter { it.name == "sr1" || it.name == "en1" })?.name)
    }

    @Test
    fun pickBestPrefersOfflineSlavicVoiceOverNetworkOne() {
        val voices = listOf(v("bs-net", "bs-BA", net = true), v("hr-off", "hr-HR", net = false))
        assertEquals("hr-off", TtsVoiceRanking.pickBest(voices)?.name)
    }

    @Test
    fun pickBestFallsBackToEnglishThenNull() {
        assertEquals("en1", TtsVoiceRanking.pickBest(listOf(v("de1", "de-DE"), v("en1", "en-GB")))?.name)
        assertNull(TtsVoiceRanking.pickBest(listOf(v("de1", "de-DE"))))
        assertNull(TtsVoiceRanking.pickBest(emptyList()))
    }
}
