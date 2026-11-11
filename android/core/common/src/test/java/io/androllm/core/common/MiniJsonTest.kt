package io.androllm.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MiniJsonTest {
    @Test
    fun parsesNestedValuesAndEscapes() {
        val obj = MiniJson.parse("{\"a\":\"x\\\"y\\n\",\"b\":[1,2.5,true,null],\"c\":{\"d\":\"\\u0041\"}}")
        val map = obj as Map<*, *>
        assertEquals("x\"y\n", map["a"])
        assertEquals(listOf(1.0, 2.5, true, null), map["b"])
        assertEquals("A", (map["c"] as Map<*, *>)["d"])
    }

    @Test
    fun rejectsTrailingGarbage() {
        assertThrows(IllegalArgumentException::class.java) { MiniJson.parse("{} x") }
    }

    @Test
    fun stringifyRoundTrips() {
        val original = mapOf(
            "tekst" to "š\"đ\\\n\u0001",
            "broj" to 1_700_000_000_123L,
            "pola" to 2.5,
            "lista" to listOf(1, true, null, "x"),
            "ugnijezdeno" to mapOf("k" to emptyList<Any>()),
        )
        val parsed = MiniJson.parse(MiniJson.stringify(original)) as Map<*, *>
        assertEquals("š\"đ\\\n\u0001", parsed["tekst"])
        assertEquals(1_700_000_000_123.0, parsed["broj"])
        assertEquals(2.5, parsed["pola"])
        assertEquals(listOf(1.0, true, null, "x"), parsed["lista"])
    }

    @Test
    fun stringifyRejectsUnknownType() {
        assertThrows(IllegalArgumentException::class.java) { MiniJson.stringify(Any()) }
    }
}
