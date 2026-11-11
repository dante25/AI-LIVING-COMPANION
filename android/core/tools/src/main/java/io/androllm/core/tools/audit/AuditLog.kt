package io.androllm.core.tools.audit

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What happened to a tool call at the permission gate. */
enum class AuditDecision {
    /** Ran without asking (policy already satisfied). */
    ALLOWED,
    /** Blocked by policy (OFF) or declined by the user. */
    DENIED,
    /** The user approved. */
    CONFIRMED
}

@Serializable
data class AuditEntry(
    val timestamp: Long,
    val tool: String,
    val permission: String? = null,
    val decision: AuditDecision,
    val sessionId: Long,
    val detail: String = ""
)

/**
 * Persistent audit log of permission decisions. One JSON object per line in
 * `filesDir/audit_log.jsonl` (append-only, survives process death). Capped at
 * [MAX_ENTRIES]; the oldest entries are dropped when the file is rewritten.
 */
@Singleton
class AuditLog(private val file: File) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, FILE_NAME))

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val _entries = MutableStateFlow<List<AuditEntry>>(emptyList())

    /** Newest entries last. Call [load] once to populate from disk. */
    val entries: StateFlow<List<AuditEntry>> = _entries.asStateFlow()

    suspend fun load() {
        mutex.withLock {
            _entries.value = withContext(Dispatchers.IO) { readAll() }
        }
    }

    suspend fun record(entry: AuditEntry) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    file.parentFile?.mkdirs()
                    file.appendText(json.encodeToString(AuditEntry.serializer(), entry) + "\n")
                }
            }
            val next = _entries.value + entry
            _entries.value = if (next.size > MAX_ENTRIES) next.takeLast(MAX_ENTRIES) else next
        }
    }

    suspend fun clear() {
        mutex.withLock {
            withContext(Dispatchers.IO) { runCatching { file.delete() } }
            _entries.value = emptyList()
        }
    }

    private fun readAll(): List<AuditEntry> {
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readLines()
                .mapNotNull { line ->
                    if (line.isBlank()) null
                    else runCatching { json.decodeFromString(AuditEntry.serializer(), line) }.getOrNull()
                }
                .takeLast(MAX_ENTRIES)
        }.getOrDefault(emptyList())
    }

    companion object {
        const val FILE_NAME = "audit_log.jsonl"
        const val MAX_ENTRIES = 2000
    }
}
