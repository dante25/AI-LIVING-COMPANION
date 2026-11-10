package alexa.packs

/**
 * Mali JSON čitač bez vanjskih biblioteka (radi i na JVM-u i na Androidu).
 * Vraća: Map<String, Any?>, List<Any?>, String, Double, Boolean ili null.
 */
object MiniJson {
    fun parse(text: String): Any? {
        val parser = Parser(text)
        val value = parser.value()
        parser.skipWs()
        require(parser.atEnd()) { "višak sadržaja nakon JSON-a" }
        return value
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd(): Boolean = i >= s.length

        fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun peek(): Char {
            skipWs()
            require(i < s.length) { "neočekivan kraj JSON-a" }
            return s[i]
        }

        private fun expect(c: Char) {
            require(peek() == c) { "očekivan znak '$c'" }
            i++
        }

        fun value(): Any? = when (peek()) {
            '{' -> obj()
            '[' -> arr()
            '"' -> str()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> num()
        }

        private fun literal(word: String, v: Any?): Any? {
            require(s.startsWith(word, i)) { "neispravan literal" }
            i += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            expect('{')
            val map = linkedMapOf<String, Any?>()
            if (peek() == '}') {
                i++
                return map
            }
            while (true) {
                val key = str()
                expect(':')
                map[key] = value()
                val c = peek()
                i++
                if (c == '}') return map
                require(c == ',') { "očekivan , ili }" }
            }
        }

        private fun arr(): List<Any?> {
            expect('[')
            val list = mutableListOf<Any?>()
            if (peek() == ']') {
                i++
                return list
            }
            while (true) {
                list.add(value())
                val c = peek()
                i++
                if (c == ']') return list
                require(c == ',') { "očekivan , ili ]" }
            }
        }

        private fun str(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                require(i < s.length) { "nezatvoren string" }
                val c = s[i++]
                if (c == '"') return sb.toString()
                if (c != '\\') {
                    sb.append(c)
                    continue
                }
                require(i < s.length) { "neispravan escape" }
                when (val e = s[i++]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'u' -> {
                        require(i + 4 <= s.length) { "neispravan unicode escape" }
                        sb.append(s.substring(i, i + 4).toInt(16).toChar())
                        i += 4
                    }
                    '"', '\\', '/' -> sb.append(e)
                    else -> throw IllegalArgumentException("neispravan escape \\$e")
                }
            }
        }

        private fun num(): Double {
            skipWs()
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            require(i > start) { "očekivan broj" }
            return s.substring(start, i).toDouble()
        }
    }
}
