package alexa.app

import alexa.memory.Memory
import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView

/**
 * Pregled memorije: potvrđene tvrdnje, brisanje pojedine tvrdnje i brisanje svega.
 * Brisanje tvrdnje ne briše poruke iz kojih je nastala.
 */
class MemoryActivity : Activity() {
    private lateinit var store: SqliteMemoryStore
    private lateinit var adapter: ArrayAdapter<String>
    private var items: List<Memory> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        title = getString(R.string.memory_title)

        store = SqliteMemoryStore(this)

        findViewById<Button>(R.id.action_button).apply {
            text = getString(R.string.memory_clear_all)
            setOnClickListener { confirmClearAll() }
        }

        val list = findViewById<ListView>(R.id.list)
        list.emptyView = findViewById(R.id.empty_text)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList<String>())
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> confirmDeleteOne(items[position]) }

        reload()
    }

    private fun reload() {
        items = store.activeMemories()
        adapter.clear()
        adapter.addAll(items.map { it.claim })
        adapter.notifyDataSetChanged()
    }

    private fun confirmDeleteOne(memory: Memory) {
        AlertDialog.Builder(this)
            .setTitle("Obrisati tvrdnju?")
            .setMessage(memory.claim)
            .setPositiveButton(R.string.delete) { _, _ ->
                store.delete(memory.id)
                reload()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setTitle("Obrisati sve?")
            .setMessage(R.string.memory_clear_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                store.deleteAll()
                reload()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
