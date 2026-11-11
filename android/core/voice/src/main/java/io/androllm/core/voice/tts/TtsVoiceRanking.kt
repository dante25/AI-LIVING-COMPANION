package io.androllm.core.voice.tts

/**
 * Opis jednog sistemskog glasa, bez Android klasa (zbog jediničnih testova).
 *
 * @property language ISO jezik glasa ("hr", "sr", "bs", "en"; dozvoljeni su i troslovni kodovi)
 * @property quality Voice.getQuality() (veća vrijednost je bolji glas)
 */
data class TtsVoiceInfo(
    val name: String,
    val languageTag: String,
    val language: String,
    val networkRequired: Boolean,
    val quality: Int = 0
)

object TtsVoiceRanking {

    /** Svodi jezik na dvoslovni kod ("hrv" -> "hr", "sr-Latn-RS" -> "sr"). */
    fun normalizeLanguage(raw: String): String {
        val base = raw.trim().lowercase().replace('_', '-').substringBefore('-')
        return when (base) {
            "hrv" -> "hr"
            "srp", "scc" -> "sr"
            "bos" -> "bs"
            "eng" -> "en"
            else -> base
        }
    }

    /** Redoslijed prikaza: hr, sr, bs, pa en, pa ostalo. */
    fun displayRank(language: String): Int = when (normalizeLanguage(language)) {
        "hr" -> 0
        "sr" -> 1
        "bs" -> 2
        "en" -> 3
        else -> 4
    }

    /** Redoslijed automatskog izbora: bs je najbliži, zatim hr, sr, pa en. */
    fun autoRank(language: String): Int = when (normalizeLanguage(language)) {
        "bs" -> 0
        "hr" -> 1
        "sr" -> 2
        "en" -> 3
        else -> 4
    }

    private val within = compareBy<TtsVoiceInfo>(
        { it.networkRequired },      // offline prvo
        { -it.quality },             // bolji kvalitet prvo
        { it.name.lowercase() }
    )

    /** Sortira: jezik po [displayRank], pa offline prije mrežnih, pa kvalitet, pa naziv. */
    fun sort(voices: List<TtsVoiceInfo>): List<TtsVoiceInfo> =
        voices.sortedWith(
            compareBy<TtsVoiceInfo> { displayRank(it.language) }
                .then(compareBy<TtsVoiceInfo> { normalizeLanguage(it.language) })
                .then(within)
        )

    /**
     * Izbor glasa: ako je [preferredName] neprazan i postoji, vraća njega.
     * Inače najbolji glas za bs/hr/sr (offline prvo), pa en; null kada nema nijednog od njih
     * (tada ostaje podrazumijevani glas motora).
     */
    fun pickBest(voices: List<TtsVoiceInfo>, preferredName: String = ""): TtsVoiceInfo? {
        if (preferredName.isNotBlank()) {
            voices.firstOrNull { it.name == preferredName }?.let { return it }
        }
        val slavic = voices
            .filter { autoRank(it.language) <= 2 }
            .sortedWith(
                compareBy<TtsVoiceInfo> { it.networkRequired }
                    .then(compareBy<TtsVoiceInfo> { autoRank(it.language) })
                    .then(compareBy<TtsVoiceInfo> { -it.quality })
                    .then(compareBy<TtsVoiceInfo> { it.name.lowercase() })
            )
        slavic.firstOrNull()?.let { return it }
        return voices.filter { autoRank(it.language) == 3 }.sortedWith(within).firstOrNull()
    }
}
