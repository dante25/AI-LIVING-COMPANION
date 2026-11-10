package alexa.voice

import alexa.chat.ChatPipeline
import alexa.memory.InMemoryMemoryStore
import alexa.model.BudgetGuard
import alexa.model.LlmAdapter
import alexa.model.Location
import alexa.model.ModelRouter
import alexa.model.ModelSettings
import alexa.model.Reply
import alexa.model.Request
import alexa.model.Tier
import alexa.soul.SoulState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SentenceSplitterTest {
    @Test
    fun splitsOnSentenceEnd() {
        val s = SentenceSplitter()
        assertEquals(listOf("Zdravo."), s.feed("Zdravo. Kako si?"))
        assertEquals("Kako si?", s.flush())
    }

    @Test
    fun keepsDecimalsTogether() {
        val s = SentenceSplitter()
        assertEquals(listOf("Cijena 3.50 KM."), s.feed("Cijena 3.50 KM. Hvala"))
        assertEquals("Hvala", s.flush())
    }

    @Test
    fun keepsEllipsisTogether() {
        val s = SentenceSplitter()
        assertEquals(listOf("Bok..."), s.feed("Bok... ok"))
    }

    @Test
    fun waitsForMoreTextAcrossChunks() {
        val s = SentenceSplitter()
        assertEquals(emptyList(), s.feed("Bo"))
        assertEquals(emptyList(), s.feed("k."))
        assertEquals(listOf("Bok."), s.feed(" Dobro"))
    }

    @Test
    fun flushOfEmptyBufferIsNull() {
        assertEquals(null, SentenceSplitter().flush())
    }
}

class TurnManagerTest {
    @Test
    fun newTurnMakesOldTurnStale() {
        val tm = TurnManager()
        val first = tm.start()
        val second = tm.start()
        assertFalse(tm.isCurrent(first))
        assertTrue(tm.isCurrent(second))
    }

    @Test
    fun cancelMakesCurrentTurnStale() {
        val tm = TurnManager()
        val id = tm.start()
        tm.cancelCurrent()
        assertFalse(tm.isCurrent(id))
    }
}

private class FakeModel(private val reply: String) : LlmAdapter {
    override val tier = Tier.WEAK
    override val location = Location.LOCAL
    var calls = 0
        private set

    override fun generate(request: Request): Reply {
        calls++
        return Reply(reply, null, 10)
    }
}

private class FakeSynth(private val onCall: (Int) -> Unit = {}) : SpeechSynthesizer {
    var calls = 0
        private set

    override fun synthesize(turnId: Long, sentence: String): AudioChunk {
        calls++
        onCall(calls)
        return AudioChunk(turnId, sentence, sentence.toByteArray())
    }
}

class ChatPipelineTest {
    private fun pipeline(
        model: FakeModel,
        synth: SpeechSynthesizer,
        memory: InMemoryMemoryStore,
        turns: TurnManager = TurnManager(),
    ): ChatPipeline {
        val router = ModelRouter(listOf(model), ModelSettings(Location.LOCAL, Location.LOCAL), BudgetGuard(1000))
        return ChatPipeline("c1", SoulState(), memory, router, synth, turns)
    }

    @Test
    fun greetingGetsModelReplySpokenAndBothMessagesSaved() {
        val memory = InMemoryMemoryStore()
        val model = FakeModel("Evo me. Šta ima?")
        val synth = FakeSynth()
        val result = pipeline(model, synth, memory).handle("bok", 1000)

        assertEquals("Evo me. Šta ima?", result.replyText)
        assertEquals(2, result.audio.size)
        assertEquals(listOf("Evo me.", "Šta ima?"), result.audio.map { it.sentence })
        assertFalse(result.interrupted)
        assertEquals(2, memory.messages("c1").size)
    }

    @Test
    fun insultGetsSulkWithoutCallingModel() {
        val model = FakeModel("ne bi trebalo da se vidi")
        val result = pipeline(model, FakeSynth(), InMemoryMemoryStore()).handle("idiot si", 1000)
        assertEquals("...", result.replyText)
        assertEquals(0, model.calls)
    }

    @Test
    fun modelFailureFallsBackButUserMessageIsStillSaved() {
        val memory = InMemoryMemoryStore()
        val router = ModelRouter(emptyList(), ModelSettings(Location.LOCAL, Location.LOCAL), BudgetGuard(1000))
        val chat = ChatPipeline("c1", SoulState(), memory, router, FakeSynth(), TurnManager())
        val result = chat.handle("kako si", 1000)
        assertTrue(result.replyText.isNotBlank())
        assertEquals("kako si", memory.messages("c1").first().text)
    }

    @Test
    fun bargeInStopsSpeechAfterCurrentSentence() {
        val turns = TurnManager()
        lateinit var chat: ChatPipeline
        // korisnik prekine Alexu dok se izgovara druga rečenica
        val synth = FakeSynth { call -> if (call == 2) chat.interrupt() }
        val router = ModelRouter(
            listOf(FakeModel("Prva rečenica. Druga rečenica. Treća.")),
            ModelSettings(Location.LOCAL, Location.LOCAL),
            BudgetGuard(1000),
        )
        chat = ChatPipeline("c1", SoulState(), InMemoryMemoryStore(), router, synth, turns)

        val result = chat.handle("bok", 1000)

        assertEquals(1, result.audio.size)
        assertEquals("Prva rečenica.", result.audio.single().sentence)
        assertTrue(result.interrupted)
    }
}
