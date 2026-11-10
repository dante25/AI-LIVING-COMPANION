package alexa.memory

/**
 * Ugovor za memoriju. Implementacije: InMemoryMemoryStore (testovi) i kasnije
 * SQLite (Android, faza 5). Pravila iz plana:
 *  - poruke se nikad ne brišu;
 *  - automatski izvučena tvrdnja počinje kao prijedlog i nije aktivna dok je se ne potvrdi;
 *  - izričita ispravka korisnika zamjenjuje staru tvrdnju, stara ostaje u tragu;
 *  - svaka tvrdnja navodi izvorne poruke.
 */
enum class MemoryStatus { SUGGESTED, CONFIRMED, CORRECTED }

/** Vrsta tvrdnje. Odnos se pamti, ne samo činjenice: šale, svađe, što joj smeta. */
enum class MemoryKind { FACT, PREFERENCE, SHARED_MOMENT, CONFLICT, DISLIKE }

data class Message(
    val id: String,
    val conversationId: String,
    val role: String,      // "user" ili "alexa"
    val text: String,
    val timeMillis: Long,
)

data class Memory(
    val id: String,
    val claim: String,
    val sourceMessageIds: List<String>,
    val status: MemoryStatus,
    val supersedes: String?,
    val timeMillis: Long,
    val kind: MemoryKind = MemoryKind.FACT,
)

interface MemoryStore {
    /** Idempotentno: ponovljena poruka se ne dodaje dvaput. */
    fun addMessage(message: Message)
    fun messages(conversationId: String): List<Message>

    fun suggest(
        claim: String,
        sourceMessageIds: List<String>,
        timeMillis: Long,
        kind: MemoryKind = MemoryKind.FACT,
    ): Memory
    fun confirm(memoryId: String): Memory
    fun correct(oldMemoryId: String, newClaim: String, sourceMessageIds: List<String>, timeMillis: Long): Memory
    fun delete(memoryId: String)

    fun memory(id: String): Memory?
    /** Samo potvrđene i nezamijenjene tvrdnje. Ovo ide u kontekst modela. */
    fun activeMemories(): List<Memory>
    fun search(query: String): List<Memory>
}
