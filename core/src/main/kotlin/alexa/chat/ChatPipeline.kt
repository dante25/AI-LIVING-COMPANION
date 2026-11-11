package alexa.chat

import alexa.memory.Message
import alexa.memory.MemoryStore
import alexa.model.ModelException
import alexa.model.ModelRouter
import alexa.model.Request
import alexa.soul.SoulState
import alexa.soul.classify
import alexa.soul.decide
import alexa.soul.onEvent
import alexa.voice.AudioChunk
import alexa.voice.SentenceSplitter
import alexa.voice.SpeechSynthesizer
import alexa.voice.TurnManager

data class TurnResult(
    val turnId: Long,
    val replyText: String,
    val audio: List<AudioChunk>,
    val interrupted: Boolean,
)

private val FIXED_REPLIES = mapOf(
    "sulk" to "...",
    "refuse" to "Ne da mi se. Pitaj me kasnije.",
    "seek_attention" to "Halo?! Gledaj me!",
    "sleep" to "*zijevne* Ja bih malo da spavam.",
)

private const val FALLBACK_REPLY = "Ne mogu sad da razmislim. Probaj malo kasnije."

/**
 * Jedan chat tok: poruka → duša → model → memorija → govor po rečenicama.
 * Radi bez Androida: mikrofon i zvučnik su iza ugovora (SpeechSynthesizer).
 */
class ChatPipeline(
    private val conversationId: String,
    private val soul: SoulState,
    private val memory: MemoryStore,
    private val router: ModelRouter,
    private val synth: SpeechSynthesizer,
    private val turns: TurnManager,
) {
    fun handle(userText: String, now: Long): TurnResult {
        val turnId = turns.start()

        val kind = classify(userText)
        soul.onEvent(kind)
        val decision = decide(soul, kind)

        // id mora biti jedinstven i nakon ponovnog pokretanja, pa se uključuje vrijeme
        memory.addMessage(Message("u-$now-$turnId", conversationId, "user", userText, now))

        val replyText = if (decision.action == "respond") {
            askModel()
        } else {
            FIXED_REPLIES[decision.action] ?: FALLBACK_REPLY
        }

        memory.addMessage(Message("a-$now-$turnId", conversationId, "alexa", replyText, now))

        val audio = speak(turnId, replyText)
        return TurnResult(turnId, replyText, audio, interrupted = !turns.isCurrent(turnId))
    }

    /** Korisnik je prekinuo Alexu govorom: tekući turn se zaustavlja. */
    fun interrupt() {
        turns.cancelCurrent()
    }

    private fun askModel(): String {
        val recent = memory.messages(conversationId).takeLast(10).map { "${it.role}: ${it.text}" }
        return try {
            router.answer(Request(system = systemPrompt(), messages = recent)).text
        } catch (e: ModelException) {
            FALLBACK_REPLY
        }
    }

    private fun speak(turnId: Long, text: String): List<AudioChunk> {
        val splitter = SentenceSplitter()
        val sentences = splitter.feed(text) + listOfNotNull(splitter.flush())
        val out = mutableListOf<AudioChunk>()
        for (sentence in sentences) {
            if (!turns.isCurrent(turnId)) break
            val chunk = synth.synthesize(turnId, sentence)
            // zvuk napravljen dok je turn zastario ne smije otići na zvučnik
            if (!turns.isCurrent(turnId)) break
            out.add(chunk)
        }
        return out
    }

    private fun systemPrompt(): String =
        "Ti si Alexa, razigrana i šašava saputnica. Odgovaraj kratko, prirodno i na bosanskom. " +
            "Raspoloženje: ${label(soul.mood)}. Dosada: ${label(soul.boredom)}. Ljutnja: ${label(soul.anger)}."

    private fun label(value: Double): String = when {
        value < 33 -> "nisko"
        value < 66 -> "srednje"
        else -> "visoko"
    }
}
