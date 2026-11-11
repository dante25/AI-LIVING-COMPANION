package io.androllm.core.common

/**
 * Mali JSON čitač i pisač bez vanjskih biblioteka. Radi isto na JVM-u (unit testovi)
 * i na Androidu, za razliku od org.json koji se u JVM testovima ne izvršava.
 *
 * [parse] vraća: Map<String, Any?>, List<Any?>, String, Double, Boolean ili null.
 * [stringify] prima iste tipove plus bilo koji [Number] i [Iterable].
 */
object MiniJson {
    fun parse(text: String): Any? {
        val parser = Parser(text)
        val value = parser.value()
        parser.skipWs()
        require(parser.atEnd()) { "višak sadržaja nakon JSON-a" }
        return value
    }

    /** Pretvara mape, liste, tekst, brojeve, boolean i null u JSON tekst. */
    fun stringify(value: Any?): String {
        val sb = StringBuilder()
        write(sb, value)
        return sb.toString()
    }

    private fun write(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is Boolean -> sb.append(if (value) "true" else "false")
            is String -> quote(sb, value)
            is Number -> writeNumber(sb, value)
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    quote(sb, k as? String ?: throw IllegalArgumentException("ključ nije tekst"))
                    sb.append(':')
                    write(sb, v)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (v in value) {
                    if (!first) sb.append(',')
                    first = false
                    write(sb, v)
                }
                sb.append(']')
            }
            else -> throw IllegalArgumentException("tip se ne može upisati u JSON: ${value::class.java.name}")
        }
    }

    private fun writeNumber(sb: StringBuilder, n: Number) {
        when (n) {
            is Int, is Long, is Short, is Byte -> sb.append(n.toLong())
            else -> {
                val d = n.toDouble()
                require(d.isFinite()) { "NaN i beskonačnost nisu dozvoljeni u JSON-u" }
                if (d == Math.rint(d) && Math.abs(d) < 9.0e15) sb.append(d.toLong()) else sb.append(d)
            }
        }
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') {
                    sb.append("\\u").append(String.format("%04x", c.code))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
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
                        val code = s.substring(i, i + 4).toIntOrNull(16)
                            ?: throw IllegalArgumentException("neispravan unicode escape")
                        sb.append(code.toChar())
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
            return s.substring(start, i).toDoubleOrNull() ?: throw IllegalArgumentException("neispravan broj")
        }
    }
}
