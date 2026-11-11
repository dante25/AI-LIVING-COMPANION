package alexa.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import java.io.File

/**
 * Lokalni modeli: uvoz fajla i brisanje. Brisanje briše samo fajl modela.
 * Memorija i razgovori ostaju. Pokretanje modela još nije podržano.
 */
class ModelsActivity : Activity() {
    private lateinit var storage: ModelStorage
    private lateinit var adapter: ArrayAdapter<String>
    private var files: List<File> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        title = getString(R.string.models_title)

        storage = ModelStorage(File(filesDir, "models"))

        findViewById<Button>(R.id.action_button).apply {
            text = getString(R.string.models_import)
            setOnClickListener { pickModel() }
        }

        val list = findViewById<ListView>(R.id.list)
        list.emptyView = findViewById(R.id.empty_text)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList<String>())
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> confirmDelete(files[position]) }

        reload()
    }

    private fun reload() {
        files = storage.list()
        adapter.clear()
        adapter.addAll(files.map { "${it.name}  (${it.length() / (1024 * 1024)} MB)" })
        adapter.notifyDataSetChanged()
    }

    private fun pickModel() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
        startActivityForResult(intent, REQUEST_MODEL)
    }

    @Deprecated("Koristi se za jednostavnost; prelazak na Activity Result API ide uz UI fazu.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MODEL && resultCode == RESULT_OK) {
            data?.data?.let { importModel(it) }
        }
    }

    /** Fajl se kopira u streamu, jer modeli znaju biti veliki. */
    private fun importModel(uri: Uri) {
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "model.bin"
        try {
            val copied = contentResolver.openInputStream(uri)?.use { storage.importStream(name, it) }
            if (copied == null) toast("Model se ne može otvoriti")
            else toast("Model uvezen: $name")
            reload()
        } catch (e: Exception) {
            toast("Uvoz nije uspio: ${e.message}")
        }
    }

    private fun confirmDelete(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Obrisati model?")
            .setMessage("Briše se samo fajl ${file.name}. Memorija i razgovori ostaju.")
            .setPositiveButton(R.string.delete) { _, _ ->
                storage.delete(file)
                reload()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val REQUEST_MODEL = 3
    }
}
