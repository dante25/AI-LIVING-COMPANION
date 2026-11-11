package alexa.app

import alexa.model.CatalogEntry
import alexa.model.ModelCatalog
import alexa.permissions.AuditLog
import alexa.permissions.Capability
import alexa.permissions.PermissionGate
import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Toast
import java.io.File

/**
 * Katalog modela za preuzimanje. Preuzimanje traži uključen prekidač "Internet"
 * u Postavkama i potvrdu korisnika. Svako preuzimanje se bilježi u dnevnik.
 */
class CatalogActivity : Activity() {
    private lateinit var appSettings: AppSettings
    private lateinit var gate: PermissionGate
    private lateinit var downloader: ModelDownloader
    private val entries: List<CatalogEntry> = ModelCatalog.entries

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        title = getString(R.string.catalog_title)

        appSettings = AppSettings(this)
        gate = PermissionGate(appSettings.load(), AuditLog())
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

    private fun confirmDownload(entry: CatalogEntry) {
        val description = "preuzimanje ${entry.name} (${entry.sizeMb} MB)"
        if (!appSettings.isEnabled(Capability.INTERNET)) {
            gate.request(Capability.INTERNET, now()) { false } // bilježi se u dnevnik
            toast("Uključi Internet u Postavkama da bi se model preuzeo")
            return
        }
        val warning = if (entry.sha256 == null) {
            "\nSažetak fajla još nije provjeren. Preuzimanje se ipak izvodi, ali fajl nije potvrđen."
        } else {
            ""
        }
        AlertDialog.Builder(this)
            .setTitle("Preuzeti model?")
            .setMessage("${entry.name}\n${entry.sizeMb} MB. Preuzima se s interneta.$warning")
            .setPositiveButton("Preuzmi") { _, _ ->
                if (gate.confirmAction(description, now()) { true }) startDownload(entry)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                gate.confirmAction(description, now()) { false }
            }
            .show()
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
