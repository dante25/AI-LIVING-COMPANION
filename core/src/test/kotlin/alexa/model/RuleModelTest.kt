package alexa.model

import alexa.soul.SoulState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun ask(model: RuleModel, text: String): String =
    model.generate(Request(system = "", messages = listOf("user: $text"))).text

class RuleModelTest {
    @Test
    fun howAreYouFollowsMood() {
        assertEquals("Odlično, a ti?", ask(RuleModel(SoulState()), "kako si?"))
    }

    @Test
    fun angryAlexaSaysSo() {
        assertTrue(ask(RuleModel(SoulState(anger = 50.0)), "kako si?").contains("ljuta"))
    }

    @Test
    fun identityAnswer() {
        assertTrue(ask(RuleModel(SoulState()), "ko si ti?").contains("Alexa"))
    }

    @Test
    fun emptyInputGetsAnswer() {
        assertEquals("Nisam te čula.", ask(RuleModel(SoulState()), "   "))
    }

    @Test
    fun sameInputSameReply() {
        val model = RuleModel(SoulState())
        assertEquals(ask(model, "ajde reci nešto"), ask(model, "ajde reci nešto"))
    }

    @Test
    fun repliesAreAlwaysValid() {
        val model = RuleModel(SoulState())
        for (text in listOf("bok", "šta radiš?", "hvala ti", "zašto?", "kako si", "nešto nasumično")) {
            assertTrue(isValidReply(model.generate(Request("", listOf("user: $text")))), text)
        }
    }
}
