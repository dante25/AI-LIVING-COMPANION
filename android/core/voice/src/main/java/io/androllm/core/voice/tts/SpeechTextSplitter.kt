package io.androllm.core.voice.tts

/**
 * Čista logika (bez Androida) za pripremu teksta odgovora za čitanje naglas:
 * čišćenje markdowna i emoji znakova te dijeljenje na rečenice.
 */
object SpeechTextSplitter {

    /** Najveća dužina jednog komada koji se šalje motoru za govor. */
    const val MAX_CHUNK_CHARS = 280

    private val linkRegex = Regex("""\[([^\]]*)]\([^)]*\)""")
    private val lineMarkerRegex = Regex("""^[ \t]*(?:[-+>]|\d+\.)[ \t]+""")
    private val spacesRegex = Regex("""[ \t]+""")

    /** Uklanja markdown znakove (* # `), veze i emoji; zadržava nove redove. */
    fun clean(text: String): String {
        val noLinks = linkRegex.replace(text.replace("\r", ""), "$1")
        val sb = StringBuilder(noLinks.length)
        var i = 0
        while (i < noLinks.length) {
            val cp = noLinks.codePointAt(i)
            i += Character.charCount(cp)
            if (cp == '*'.code || cp == '#'.code || cp == '`'.code) continue
            if (isEmojiLike(cp)) continue
            sb.appendCodePoint(cp)
        }
        return sb.toString()
            .lines()
            .joinToString("\n") { line ->
                spacesRegex.replace(lineMarkerRegex.replace(line, ""), " ").trim()
            }
    }

    /** Čisti tekst i dijeli ga na rečenice (. ! ? … i novi red). */
    fun splitSentences(text: String): List<String> {
        val result = mutableListOf<String>()
        for (line in clean(text).split('\n')) {
            if (line.isBlank()) continue
            val current = StringBuilder()
            for ((index, ch) in line.withIndex()) {
                current.append(ch)
                if (ch == '.' || ch == '!' || ch == '?' || ch == '…') {
                    val next = line.getOrNull(index + 1)
                    if (next == null || next.isWhitespace()) {
                        addChunk(result, current.toString())
                        current.setLength(0)
                    }
                }
            }
            addChunk(result, current.toString())
        }
        return result
    }

    private fun addChunk(out: MutableList<String>, raw: String) {
        var rest = raw.trim()
        while (rest.length > MAX_CHUNK_CHARS) {
            var cut = rest.lastIndexOf(' ', MAX_CHUNK_CHARS)
            if (cut <= 0) cut = MAX_CHUNK_CHARS
            val piece = rest.substring(0, cut).trim()
            if (piece.any { it.isLetterOrDigit() }) out.add(piece)
            rest = rest.substring(cut).trim()
        }
        if (rest.any { it.isLetterOrDigit() }) out.add(rest)
    }

    private fun isEmojiLike(cp: Int): Boolean =
        cp in 0x1F000..0x1FAFF ||
            cp in 0x2600..0x27BF ||
            cp in 0x2B00..0x2BFF ||
            cp == 0xFE0F || cp == 0x200D || cp == 0x20E3
}
