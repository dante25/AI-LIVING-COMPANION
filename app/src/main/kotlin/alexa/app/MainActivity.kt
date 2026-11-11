package alexa.app

import alexa.chat.ChatPipeline
import alexa.model.BudgetGuard
import alexa.model.Location
import alexa.model.ModelRouter
import alexa.model.ModelSettings
import alexa.model.RuleModel
import alexa.packs.ContentStore
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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Locale

/**
 * Glavni ekran: razgovor tekstom i glasom. Postavke, memorija i lokalni modeli
 * su u zasebnim ekranima (meni gore desno).
 *
 * Sesija prati pravila iz core-a: chat je aktivan dok je ekran otvoren, a tajmer
 * tišine i pravilo od 1 h se provjeravaju dok je aplikacija vidljiva.
 */
class MainActivity : Activity() {
    private lateinit var log: TextView
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var store: SqliteMemoryStore
    private lateinit var chat: ChatPipeline
    private lateinit var sessions: SessionManager
    private lateinit var gate: PermissionGate
    private lateinit var permissionSettings: PermissionSettings
    private lateinit var appSettings: AppSettings
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
        setContentView(R.layout.activity_main)

        log = findViewById(R.id.log)
        scroll = findViewById(R.id.scroll)
        input = findViewById(R.id.input)
        findViewById<ImageButton>(R.id.send).setOnClickListener {
            val text = input.text.toString()
            input.setText("")
            sendText(text)
        }
        findViewById<ImageButton>(R.id.mic).setOnClickListener { useMicrophone() }

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
        gate = PermissionGate(permissionSettings, AuditLog())
        sessions = SessionManager(gate)

        // Uvezeni paketi se ponovo učitavaju pri svakom pokretanju.
        val content = ContentStore()
        PackStorage(File(filesDir, "packs")).loadAll().forEach { content.importZip(it) }

        val soul = SoulState()
        val router = ModelRouter(
            listOf(RuleModel(soul)),
            ModelSettings(Location.LOCAL, Location.LOCAL), // oblak nije aktivan, pa je rad lokalni
            BudgetGuard(1000),
        )
        chat = ChatPipeline(CONVERSATION, soul, store, router, TtsSynth(), TurnManager())

        store.messages(CONVERSATION).takeLast(HISTORY_SHOWN).forEach { m ->
            appendLine((if (m.role == "user") "ti: " else "alexa: ") + m.text)
        }
    }

    override fun onResume() {
        super.onResume()
        // Prekidači su mogli biti promijenjeni u Postavkama; odmah ih primijeni.
        Capability.entries.forEach { permissionSettings.set(it, appSettings.isEnabled(it)) }
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        R.id.menu_memory -> {
            startActivity(Intent(this, MemoryActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
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

    /**
     * Glas: mikrofon se koristi tek kad je uključen u Postavkama. Ako dozvola
     * još nije data u ovoj sesiji, pita se jednom, a odgovor se bilježi u dnevnik.
     */
    private fun useMicrophone() {
        val now = System.currentTimeMillis()
        when {
            !permissionSettings.isOn(Capability.MICROPHONE) -> {
                gate.request(Capability.MICROPHONE, now) { false } // bilježi se u dnevnik
                toast("Mikrofon je isključen u Postavkama")
            }
            gate.hasSessionGrant(Capability.MICROPHONE) -> startListening()
            else -> AlertDialog.Builder(this)
                .setTitle("Dozvoliti mikrofon?")
                .setMessage("Važi do kraja sesije. Zatvaranjem chata se resetuje.")
                .setPositiveButton("Dozvoli") { _, _ ->
                    if (gate.request(Capability.MICROPHONE, now) { true }) startListening()
                }
                .setNegativeButton("Ne") { _, _ ->
                    gate.request(Capability.MICROPHONE, now) { false }
                }
                .show()
        }
    }

    private fun startListening() {
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
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
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
    }
}
