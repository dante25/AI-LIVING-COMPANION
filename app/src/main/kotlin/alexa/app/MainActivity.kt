package alexa.app

import alexa.chat.ChatPipeline
import alexa.memory.InMemoryMemoryStore
import alexa.model.BudgetGuard
import alexa.model.Location
import alexa.model.ModelRouter
import alexa.model.ModelSettings
import alexa.model.RuleModel
import alexa.soul.SoulState
import alexa.voice.AudioChunk
import alexa.voice.SpeechSynthesizer
import alexa.voice.TurnManager
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/**
 * Prvi ekran: chat s glasom. Tekst ide kroz ChatPipeline iz core modula,
 * odgovor se čita naglas Android TTS-om, a glas se prepoznaje Android
 * prepoznavanjem govora (push-to-talk).
 *
 * Odgovori su pravila iz stanja duše (RuleModel). Pravi jezički model dolazi u fazi 7.
 */
class MainActivity : Activity() {
    private lateinit var log: TextView
    private lateinit var input: EditText
    private var tts: TextToSpeech? = null
    private lateinit var chat: ChatPipeline

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("bs", "BA"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
            }
        }

        val soul = SoulState()
        val router = ModelRouter(
            listOf(RuleModel(soul)),
            ModelSettings(Location.LOCAL, Location.LOCAL),
            BudgetGuard(1000),
        )
        chat = ChatPipeline("glavni", soul, InMemoryMemoryStore(), router, TtsSynth(), TurnManager())

        log = TextView(this).apply { textSize = 16f }
        val scroll = ScrollView(this).apply {
            addView(log)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        input = EditText(this).apply {
            hint = "Napiši Alexi"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sendButton = Button(this).apply {
            text = "Pošalji"
            setOnClickListener {
                val text = input.text.toString()
                input.setText("")
                sendText(text)
            }
        }
        val micButton = Button(this).apply {
            text = "Govori"
            setOnClickListener { listen() }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(input)
            addView(sendButton)
            addView(micButton)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            addView(scroll)
            addView(row)
        }
        setContentView(root)
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    private fun sendText(text: String) {
        if (text.isBlank()) return
        tts?.stop() // novi turn poništava stari govor
        val result = chat.handle(text, System.currentTimeMillis())
        appendLine("ti: $text")
        appendLine("alexa: ${result.replyText}")
    }

    private fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Govori")
        startActivityForResult(intent, REQUEST_SPEECH)
    }

    @Deprecated("Koristi se za jednostavnost; prelazak na Activity Result API ide uz UI fazu.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SPEECH && resultCode == RESULT_OK) {
            val heard = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (heard != null) sendText(heard)
        }
    }

    private fun appendLine(line: String) {
        log.append(line + "\n\n")
    }

    /** Izgovara rečenicu odmah na Android TTS-u. Audio se ne vraća kao bajtovi. */
    private inner class TtsSynth : SpeechSynthesizer {
        override fun synthesize(turnId: Long, sentence: String): AudioChunk {
            tts?.speak(sentence, TextToSpeech.QUEUE_ADD, null, "turn-$turnId-${sentence.hashCode()}")
            return AudioChunk(turnId, sentence, ByteArray(0))
        }
    }

    companion object {
        private const val REQUEST_SPEECH = 1
    }
}
