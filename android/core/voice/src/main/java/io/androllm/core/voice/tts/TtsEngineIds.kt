package io.androllm.core.voice.tts

/** Identifikatori motora za sintezu govora, kako se čuvaju u VoiceSettings.ttsEngine. */
object TtsEngineIds {
    const val SYSTEM = "system"
    const val PIPER = "piper"
    const val GEMINI = "gemini"

    /** Nepoznata ili prazna vrijednost se svodi na sistemski glas. */
    fun normalize(id: String?): String = when (id) {
        PIPER -> PIPER
        GEMINI -> GEMINI
        else -> SYSTEM
    }
}
