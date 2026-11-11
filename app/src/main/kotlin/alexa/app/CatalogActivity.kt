package alexa.app

import alexa.model.CatalogEntry
import alexa.model.ModelCatalog
import android.app.Activity
import android.app.DownloadManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * Katalog modela. Klik pokreće preuzimanje u sistemu (bez dozvole i bez dodatnog pitanja).
 * Preuzimanje ostaje aktivno i kad zatvoriš ekran, rotiraš telefon ili izađeš iz aplikacije.
 */
class CatalogActivity : Activity() {
    private val entries: List<CatalogEntry> = ModelCatalog.entries
    private lateinit var adapter: ArrayAdapter<String>
    private val handler = Handler(Looper.getMainLooper())
    private val refresher = object : Runnable {
        override fun run() {
            refreshRows()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        title = getString(R.string.catalog_title)

        findViewById<Button>(R.id.action_button).visibility = View.GONE
        findViewById<Button>(R.id.action_button_secondary).visibility = View.GONE
        findViewById<TextView>(R.id.empty_text).visibility = View.GONE

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList<String>())
        val list = findViewById<ListView>(R.id.list)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> startDownload(entries[position]) }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresher)
    }

    override fun onPause() {
        handler.removeCallbacks(refresher)
        super.onPause()
    }

    private fun startDownload(entry: CatalogEntry) {
        when {
            ModelDownloads.state(this, entry.id) != null -> toast("Već se preuzima: ${entry.name}")
            File(ModelDownloads.modelsDir(this), entry.fileName).exists() -> toast("Već je preuzet: ${entry.name}")
            else -> {
                ModelDownloads.enqueue(this, entry)
                toast("Preuzimanje počelo. Napredak je u obavijestima.")
                refreshRows()
            }
        }
    }

    private fun refreshRows() {
        adapter.clear()
        adapter.addAll(entries.map { rowText(it) })
        adapter.notifyDataSetChanged()
    }

    private fun rowText(entry: CatalogEntry): String {
        val state = ModelDownloads.state(this, entry.id)
        val status = when {
            state != null && state.status == DownloadManager.STATUS_PAUSED -> "pauzirano, ${state.percent}%"
            state != null && state.status == DownloadManager.STATUS_PENDING -> "čeka na mrežu"
            state != null -> "preuzima se ${state.percent}%"
            File(ModelDownloads.modelsDir(this), entry.fileName).exists() -> "već preuzet"
            else -> "klikni za preuzimanje"
        }
        return "${entry.name}\n${entry.sizeMb} MB · $status\n${entry.note}"
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val REFRESH_MS = 2_000L
    }
}
