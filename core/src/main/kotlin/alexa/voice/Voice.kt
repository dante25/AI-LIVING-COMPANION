package alexa.voice

/**
 * Glas: ugovori za govor, dijeljenje odgovora na rečenice i upravljanje turn-ovima.
 *
 * Pravila (iz plana):
 *  - STT i TTS su zamjenjivi moduli iza ugovora;
 *  - odgovor se izgovara po rečenicama, da prvi zvuk stigne brzo;
 *  - prekid (barge-in) poništava tekući turn, a zvuk starog turna se ne smije poslati.
 */
data class Transcript(val text: String, val confidence: Double)

interface SpeechRecognizer {
    fun recognize(audio: ByteArray): Transcript
}

data class AudioChunk(val turnId: Long, val sentence: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is AudioChunk && turnId == other.turnId && sentence == other.sentence &&
            bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * (31 * turnId.hashCode() + sentence.hashCode()) + bytes.contentHashCode()
}

interface SpeechSynthesizer {
    fun synthesize(turnId: Long, sentence: String): AudioChunk
}

/** Turn je jedan odgovor Alexe. Novi turn ili prekid čini sve prethodne zastarjelim. */
class TurnManager {
    private var generation = 0L

    fun start(): Long {
        generation++
        return generation
    }

    /** Prekid od korisnika: tekući turn postaje zastario. */
    fun cancelCurrent() {
        generation++
    }

    fun isCurrent(turnId: Long): Boolean = turnId == generation
}

/**
 * Dijeli tekst koji dolazi u komadima na rečenice.
 * Čuva decimale (3.50) i višestruke tačke (...), i ne izbacuje nepotpuni kraj
 * dok ne stigne sljedeći komad ili flush.
 */
class SentenceSplitter {
    private val buffer = StringBuilder()

    fun feed(chunk: String): List<String> {
        buffer.append(chunk)
        val out = mutableListOf<String>()
        var i = 0
        while (i < buffer.length) {
            val c = buffer[i]
            val isDecimalDot = c == '.' && i + 1 < buffer.length && buffer[i + 1].isDigit()
            if (!isEnd(c) || isDecimalDot) {
                i++
                continue
            }
            var end = i
            while (end + 1 < buffer.length && isEnd(buffer[end + 1])) end++
            if (end + 1 >= buffer.length) break // niz interpunkcije se možda nastavlja
            val sentence = buffer.substring(0, end + 1).trim()
            buffer.delete(0, end + 1)
            if (sentence.any { it.isLetterOrDigit() }) out.add(sentence)
            i = 0
        }
        return out
    }

    /** Vraća preostali tekst na kraju odgovora. */
    fun flush(): String? {
        val rest = buffer.toString().trim()
        buffer.clear()
        return rest.ifEmpty { null }
    }

    private fun isEnd(c: Char) = c == '.' || c == '!' || c == '?' || c == '…'
}
