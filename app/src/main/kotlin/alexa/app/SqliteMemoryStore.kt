package alexa.app

import alexa.memory.Memory
import alexa.memory.MemoryKind
import alexa.memory.MemoryStatus
import alexa.memory.MemoryStore
import alexa.memory.Message
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

/**
 * Trajna memorija na telefonu (SQLite). Ista pravila kao InMemoryMemoryStore iz core-a:
 * poruke se ne brišu, prijedlozi su neaktivni dok se ne potvrde, ispravka zamjenjuje staru tvrdnju.
 */
class SqliteMemoryStore(context: Context) : SQLiteOpenHelper(context, "alexa.db", null, 1), MemoryStore {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE messages (id TEXT PRIMARY KEY, conversation TEXT NOT NULL, " +
                "role TEXT NOT NULL, text TEXT NOT NULL, time INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE memories (id TEXT PRIMARY KEY, claim TEXT NOT NULL, sources TEXT NOT NULL, " +
                "status TEXT NOT NULL, supersedes TEXT, time INTEGER NOT NULL, kind TEXT NOT NULL)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    override fun addMessage(message: Message) {
        val values = ContentValues().apply {
            put("id", message.id)
            put("conversation", message.conversationId)
            put("role", message.role)
            put("text", message.text)
            put("time", message.timeMillis)
        }
        writableDatabase.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    override fun messages(conversationId: String): List<Message> {
        val out = mutableListOf<Message>()
        readableDatabase.query(
            "messages", null, "conversation = ?", arrayOf(conversationId), null, null, "time ASC",
        ).use { c ->
            while (c.moveToNext()) {
                out.add(Message(c.str("id"), c.str("conversation"), c.str("role"), c.str("text"), c.long("time")))
            }
        }
        return out
    }

    override fun suggest(claim: String, sourceMessageIds: List<String>, timeMillis: Long, kind: MemoryKind): Memory {
        requireSources(sourceMessageIds)
        val memory = Memory(newId(), claim, sourceMessageIds, MemoryStatus.SUGGESTED, null, timeMillis, kind)
        insert(memory)
        return memory
    }

    override fun confirm(memoryId: String): Memory {
        val current = requireMemory(memoryId)
        check(current.status == MemoryStatus.SUGGESTED) { "Samo prijedlog se može potvrditi" }
        writableDatabase.update(
            "memories",
            ContentValues().apply { put("status", MemoryStatus.CONFIRMED.name) },
            "id = ?",
            arrayOf(memoryId),
        )
        return current.copy(status = MemoryStatus.CONFIRMED)
    }

    override fun correct(
        oldMemoryId: String,
        newClaim: String,
        sourceMessageIds: List<String>,
        timeMillis: Long,
    ): Memory {
        val old = requireMemory(oldMemoryId)
        check(old.status != MemoryStatus.CORRECTED) { "Tvrdnja je već ispravljena" }
        requireSources(sourceMessageIds)
        val replacement = Memory(
            newId(), newClaim, sourceMessageIds, MemoryStatus.CONFIRMED, oldMemoryId, timeMillis, old.kind,
        )
        writableDatabase.beginTransaction()
        try {
            writableDatabase.update(
                "memories",
                ContentValues().apply { put("status", MemoryStatus.CORRECTED.name) },
                "id = ?",
                arrayOf(oldMemoryId),
            )
            insert(replacement)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        return replacement
    }

    /** Briše cijelu historiju razgovora i sve tvrdnje. Traži potvrdu korisnika prije poziva. */
    fun deleteAll() {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("messages", null, null)
            writableDatabase.delete("memories", null, null)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    /** Briše tvrdnju, ali ne i poruke iz kojih je nastala. */
    override fun delete(memoryId: String) {
        writableDatabase.delete("memories", "id = ?", arrayOf(memoryId))
    }

    override fun memory(id: String): Memory? = queryMemories("id = ?", arrayOf(id)).firstOrNull()

    override fun activeMemories(): List<Memory> =
        queryMemories("status = ?", arrayOf(MemoryStatus.CONFIRMED.name))

    override fun search(query: String): List<Memory> =
        activeMemories().filter { it.claim.contains(query, ignoreCase = true) }

    private fun requireMemory(id: String): Memory =
        memory(id) ?: throw IllegalArgumentException("Nepoznata tvrdnja: $id")

    private fun requireSources(ids: List<String>) {
        require(ids.isNotEmpty()) { "Tvrdnja mora imati izvorne poruke" }
        val missing = ids.filter { id ->
            readableDatabase.query("messages", arrayOf("id"), "id = ?", arrayOf(id), null, null, null).use {
                !it.moveToFirst()
            }
        }
        require(missing.isEmpty()) { "Nepoznate izvorne poruke: $missing" }
    }

    private fun insert(memory: Memory) {
        val values = ContentValues().apply {
            put("id", memory.id)
            put("claim", memory.claim)
            put("sources", memory.sourceMessageIds.joinToString("\n"))
            put("status", memory.status.name)
            put("supersedes", memory.supersedes)
            put("time", memory.timeMillis)
            put("kind", memory.kind.name)
        }
        writableDatabase.insertOrThrow("memories", null, values)
    }

    private fun queryMemories(selection: String, args: Array<String>): List<Memory> {
        val out = mutableListOf<Memory>()
        readableDatabase.query("memories", null, selection, args, null, null, "time ASC").use { c ->
            while (c.moveToNext()) out.add(c.toMemory())
        }
        return out
    }

    private fun Cursor.toMemory() = Memory(
        id = str("id"),
        claim = str("claim"),
        sourceMessageIds = str("sources").split("\n").filter { it.isNotEmpty() },
        status = MemoryStatus.valueOf(str("status")),
        supersedes = if (isNull(getColumnIndexOrThrow("supersedes"))) null else str("supersedes"),
        timeMillis = long("time"),
        kind = MemoryKind.valueOf(str("kind")),
    )

    private fun Cursor.str(column: String): String = getString(getColumnIndexOrThrow(column))
    private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

    private fun newId(): String = "mem-" + UUID.randomUUID()
}
