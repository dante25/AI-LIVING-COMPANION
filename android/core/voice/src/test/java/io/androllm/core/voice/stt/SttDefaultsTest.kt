package io.androllm.core.voice.stt

import io.androllm.core.voice.model.VoiceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SttDefaultsTest {

    @Test
    fun defaultModelIsSmallWithEnoughRam() {
        assertEquals("small", SttDefaults.defaultWhisperModelId(6))
        assertEquals("small", SttDefaults.defaultWhisperModelId(12))
    }

    @Test
    fun defaultModelIsBaseWithLittleRam() {
        assertEquals("base", SttDefaults.defaultWhisperModelId(5))
        assertEquals("base", SttDefaults.defaultWhisperModelId(2))
        assertEquals("base", SttDefaults.defaultWhisperModelId(0))
    }

    @Test
    fun defaultModelsAreMultilingualAndExist() {
        for (ram in 0L..16L) {
            val model = WhisperModels.byId(SttDefaults.defaultWhisperModelId(ram))
            assertNotNull(model)
            assertTrue(model!!.multilingual)
        }
    }

    @Test
    fun recommendedForDeviceIsAlwaysMultilingual() {
        for (ram in 0L..16L) {
            assertTrue(WhisperModels.recommendedForDevice(ram).multilingual)
        }
    }

    @Test
    fun newInstallDefaultsAreBosnian() {
        val s = VoiceSettings()
        assertEquals("bs", s.sttLanguage)
        assertEquals("bs", s.language)
        assertTrue(WhisperModels.byId(s.whisperModel)!!.multilingual)
    }

    @Test
    fun languageListHasBalkanLanguagesAndAuto() {
        assertEquals(listOf("bs", "hr", "sr", "en", "auto"), SttDefaults.LANGUAGES.map { it.code })
        assertEquals("Bosanski", SttDefaults.languageLabel("bs"))
        assertEquals("Automatski", SttDefaults.languageLabel("auto"))
        assertEquals("xx", SttDefaults.languageLabel("xx"))
    }

    @Test
    fun nextLanguageCycles() {
        assertEquals("hr", SttDefaults.nextLanguage("bs"))
        assertEquals("bs", SttDefaults.nextLanguage("auto"))
        assertEquals("bs", SttDefaults.nextLanguage("zz"))
    }

    @Test
    fun warningOnlyForEnglishOnlyModelWithNonEnglishLanguage() {
        assertTrue(SttDefaults.needsMultilingualWarning("base.en", "bs"))
        assertTrue(SttDefaults.needsMultilingualWarning("base.en", "auto"))
        assertFalse(SttDefaults.needsMultilingualWarning("base.en", "en"))
        assertFalse(SttDefaults.needsMultilingualWarning("small", "bs"))
        assertFalse(SttDefaults.needsMultilingualWarning("unknown-model", "bs"))
    }
}
