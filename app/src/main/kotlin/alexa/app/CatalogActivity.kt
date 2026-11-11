package alexa.app

import alexa.model.CatalogEntry
import alexa.model.ModelCatalog
import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Toast
import java.io.File

/**
 * Katalog modela za preuzimanje. Preuzimanje nema dozvolu: korisnik sam klikne model,
 * a napredak se vidi u dijalogu.
 */
class CatalogActivity : Activity() {
    private lateinit var downloader: ModelDownloader
    private val entries: List<CatalogEntry> = ModelCatalog.entries

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        title = getString(R.string.catalog_title)

        downloader = ModelDownloader(File(filesDir, "models"))

        findViewById<android.widget.Button>(R.id.action_button).visibility = android.view.View.GONE
        findViewById<android.widget.Button>(R.id.action_button_secondary).visibility = android.view.View.GONE
        findViewById<android.widget.TextView>(R.id.empty_text).visibility = android.view.View.GONE

        val list = findViewById<ListView>(R.id.list)
        list.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_2,
            android.R.id.text1,
            entries.map { "${it.name}\n${it.sizeMb} MB · ${it.note}" },
        )
        list.setOnItemClickListener { _, _, position, _ -> confirmDownload(entries[position]) }
    }

    /** Preuzimanje je osnovna funkcija aplikacije: klik na model je dovoljan, bez dozvole i dodatnog pitanja. */
    private fun confirmDownload(entry: CatalogEntry) {
        startDownload(entry)
    }

    private fun startDownload(entry: CatalogEntry) {
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Preuzimanje: ${entry.name}")
            .setView(bar)
            .setCancelable(false)
            .show()

        Thread {
            try {
                val file = downloader.download(entry) { p -> runOnUiThread { bar.progress = p } }
                runOnUiThread {
                    dialog.dismiss()
                    toast("Preuzeto: ${file.name}. Pokretanje modela još nije podržano.")
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    dialog.dismiss()
                    toast("Preuzimanje nije uspjelo: ${e.message}")
                }
            }
        }.start()
    }

    private fun now(): Long = System.currentTimeMillis()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
