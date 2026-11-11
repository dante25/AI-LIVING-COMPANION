package alexa.app

import alexa.chat.ChatPipeline
import alexa.model.BudgetGuard
import alexa.model.Location
import alexa.model.ModelRouter
import alexa.model.ModelSettings
import alexa.model.RuleModel
import alexa.packs.ContentStore
import alexa.packs.Verdict
import alexa.model.Integrity
import alexa.permissions.AuditLog
import alexa.permissions.Capability
import alexa.permissions.PermissionGate
import alexa.permissions.SessionManager
import alexa.permissions.Settings as PermissionSettings
import alexa.soul.SoulState
import alexa.voice.AudioChunk
import alexa.voice.SpeechSynthesizer
import alexa.voice.TurnManager
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Locale

/**
 * Glavni ekran: chat tekstom i glasom, postavke dozvola i uvoz paketa.
 * Sesija prati pravila iz core-a: chat je aktivan dok je ekran otvoren,
 * a tajmer tišine i 1 h bez obraćanja se provjeravaju periodično.
 */
class MainActivity : Activity() {
    private lateinit var log: TextView
    private lateinit var input: EditText
    private lateinit var store: SqliteMemoryStore
    private lateinit var chat: ChatPipeline
    private lateinit var sessions: SessionManager
    private lateinit var permissionSettings: PermissionSettings
    private lateinit var appSettings: AppSettings
    private lateinit var content: ContentStore
    private lateinit var packFiles: PackStorage
    private var tts: TextToSpeech? = null

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            sessions.tick(System.currentTimeMillis())
            handler.postDelayed(this, TICK_MS)
        }
    }

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

        store = SqliteMemoryStore(this)
        appSettings = AppSettings(this)
        permissionSettings = appSettings.load()
        sessions = SessionManager(PermissionGate(permissionSettings, AuditLog()))

        content = ContentStore()
        packFiles = PackStorage(File(filesDir, "packs"))
        packFiles.loadAll().forEach { content.importZip(it) }

        val soul = SoulState()
        val router = ModelRouter(
            listOf(RuleModel(soul)),
            ModelSettings(Location.LOCAL, Location.LOCAL),
            BudgetGuard(1000),
        )
        chat = ChatPipeline(CONVERSATION, soul, store, router, TtsSynth(), TurnManager())

        buildUi()
        store.messages(CONVERSATION).takeLast(HISTORY_SHOWN).forEach { m ->
            appendLine((if (m.role == "user") "ti: " else "alexa: ") + m.text)
        }
    }

    override fun onStart() {
        super.onStart()
        sessions.openChat(System.currentTimeMillis())
        handler.post(ticker)
    }

    override fun onStop() {
        handler.removeCallbacks(ticker)
        sessions.closeChat(System.currentTimeMillis())
        super.onStop()
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    private fun buildUi() {
        log = TextView(this).apply { textSize = 16f }
        val scroll = ScrollView(this).apply {
            addView(log)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val settingsButton = Button(this).apply {
            text = "Dozvole"
            setOnClickListener { showPermissions() }
        }
        val packsButton = Button(this).apply {
            text = "Uvezi paket"
            setOnClickListener { pickPack() }
        }
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(settingsButton)
            addView(packsButton)
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
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(input)
            addView(sendButton)
            addView(micButton)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            addView(topRow)
            addView(scroll)
            addView(inputRow)
        }
        setContentView(root)
    }

    private fun sendText(text: String) {
        if (text.isBlank()) return
        val now = System.currentTimeMillis()
        sessions.contact(now)
        tts?.stop() // novi turn poništava stari govor
        val result = chat.handle(text, now)
        appendLine("ti: $text")
        appendLine("alexa: ${result.replyText}")
    }

    private fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Govori")
        startActivityForResult(intent, REQUEST_SPEECH)
    }

    /** Dozvole u postavkama: važe za sesiju, a prekidači se pamte. */
    private fun showPermissions() {
        val caps = Capability.entries.toTypedArray()
        val checked = BooleanArray(caps.size) { permissionSettings.isOn(caps[it]) }
        AlertDialog.Builder(this)
            .setTitle("Dozvole (važe u sesiji)")
            .setMultiChoiceItems(caps.map { it.name }.toTypedArray(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Sačuvaj") { _, _ ->
                caps.forEachIndexed { i, capability ->
                    permissionSettings.set(capability, checked[i])
                    appSettings.save(capability, checked[i])
                }
                toast("Dozvole sačuvane")
            }
            .setNegativeButton("Odustani", null)
            .show()
    }

    private fun pickPack() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
        startActivityForResult(intent, REQUEST_PACK)
    }

    private fun importPack(uri: android.net.Uri) {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null) {
            toast("Paket se ne može otvoriti")
            return
        }
        val verdict = content.importZip(bytes)
        val message = when (verdict) {
            is Verdict.Accepted -> {
                packFiles.save(Integrity.sha256Hex(bytes) + ".zip", bytes)
                "Paket uvezen: ${verdict.manifest.name}"
            }
            is Verdict.Preserved -> {
                packFiles.save(Integrity.sha256Hex(bytes) + ".zip", bytes)
                "Nepoznat tip, sačuvan kao neaktivan: ${verdict.manifest.name}"
            }
            is Verdict.Rejected -> "Odbijeno: ${verdict.reason}"
        }
        appendLine("sistem: $message")
        toast(message)
    }

    @Deprecated("Koristi se za jednostavnost; prelazak na Activity Result API ide uz UI fazu.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQUEST_SPEECH -> {
                val heard = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                if (heard != null) sendText(heard)
            }
            REQUEST_PACK -> data?.data?.let { importPack(it) }
        }
    }

    private fun appendLine(line: String) {
        log.append(line + "\n\n")
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /** Izgovara rečenicu odmah na Android TTS-u. Audio se ne vraća kao bajtovi. */
    private inner class TtsSynth : SpeechSynthesizer {
        override fun synthesize(turnId: Long, sentence: String): AudioChunk {
            tts?.speak(sentence, TextToSpeech.QUEUE_ADD, null, "turn-$turnId-${sentence.hashCode()}")
            return AudioChunk(turnId, sentence, ByteArray(0))
        }
    }

    companion object {
        private const val CONVERSATION = "glavni"
        private const val HISTORY_SHOWN = 30
        private const val TICK_MS = 30_000L
        private const val REQUEST_SPEECH = 1
        private const val REQUEST_PACK = 2
    }
}
