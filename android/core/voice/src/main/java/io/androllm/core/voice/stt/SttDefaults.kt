package io.androllm.core.voice.stt

/** Jezik prepoznavanja govora koji korisnik može izabrati. */
data class SttLanguageOption(val code: String, val label: String)

/**
 * Čista (bez Androida) logika za podrazumijevani Whisper model i jezik.
 */
object SttDefaults {

    /** Podrazumijevani Whisper jezik za nove instalacije (bosanski). */
    const val DEFAULT_STT_LANGUAGE = "bs"

    /** Podrazumijevani jezik govora (VoiceSettings.language). */
    const val DEFAULT_SPEECH_LANGUAGE = "bs"

    /** Višejezični model koji se koristi kad nema dovoljno RAM-a. */
    const val FALLBACK_MODEL_ID = "base"

    /** Najmanje RAM-a (GB) za podrazumijevani "small" model. */
    const val SMALL_MODEL_MIN_RAM_GB = 6L

    const val AUTO = "auto"

    val LANGUAGES: List<SttLanguageOption> = listOf(
        SttLanguageOption("bs", "Bosanski"),
        SttLanguageOption("hr", "Hrvatski"),
        SttLanguageOption("sr", "Srpski"),
        SttLanguageOption("en", "Engleski"),
        SttLanguageOption(AUTO, "Automatski")
    )

    /** Podrazumijevani VIŠEJEZIČNI model: "small" uz dovoljno RAM-a, inače "base". */
    fun defaultWhisperModelId(totalRamGb: Long): String =
        if (totalRamGb >= SMALL_MODEL_MIN_RAM_GB) "small" else FALLBACK_MODEL_ID

    /** Bosanski naziv jezika za šifru; nepoznata šifra se vraća neizmijenjena. */
    fun languageLabel(code: String): String {
        val c = code.trim().lowercase()
        return LANGUAGES.firstOrNull { it.code == c }?.label ?: code
    }

    /** Sljedeći jezik u listi (ciklično); nepoznata šifra vodi na prvi. */
    fun nextLanguage(current: String): String {
        val idx = LANGUAGES.indexOfFirst { it.code == current.trim().lowercase() }
        return if (idx >= 0) LANGUAGES[(idx + 1) % LANGUAGES.size].code else LANGUAGES.first().code
    }

    /**
     * True kad izabrani model razumije samo engleski, a jezik nije engleski
     * (uključujući "auto", jer korisnik tada može govoriti bilo koji jezik).
     * Nepoznat model se ne upozorava.
     */
    fun needsMultilingualWarning(modelId: String, sttLanguage: String): Boolean {
        val model = WhisperModels.byId(modelId) ?: return false
        if (model.multilingual) return false
        return sttLanguage.trim().lowercase() != "en"
    }

    const val ENGLISH_ONLY_WARNING =
        "Izabrani model razumije samo engleski. Izaberi višejezični model (Base, Small ...)"
}
