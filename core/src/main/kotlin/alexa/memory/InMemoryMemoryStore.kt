package alexa.memory

/** Memorija u RAM-u. Služi za testove i simulator; trajno skladište je SQLite. */
class InMemoryMemoryStore : MemoryStore {
    private val messagesById = linkedMapOf<String, Message>()
    private val memoriesById = linkedMapOf<String, Memory>()
    private var counter = 0

    private fun nextId(): String = "mem-${++counter}"

    override fun addMessage(message: Message) {
        if (message.id in messagesById) return
        messagesById[message.id] = message
    }

    override fun messages(conversationId: String): List<Message> =
        messagesById.values
            .filter { it.conversationId == conversationId }
            .sortedBy { it.timeMillis }

    override fun suggest(claim: String, sourceMessageIds: List<String>, timeMillis: Long): Memory {
        requireSources(sourceMessageIds)
        val memory = Memory(nextId(), claim, sourceMessageIds, MemoryStatus.SUGGESTED, null, timeMillis)
        memoriesById[memory.id] = memory
        return memory
    }

    override fun confirm(memoryId: String): Memory {
        val current = require(memoryId)
        check(current.status == MemoryStatus.SUGGESTED) { "Samo prijedlog se može potvrditi" }
        return current.copy(status = MemoryStatus.CONFIRMED).also { memoriesById[memoryId] = it }
    }

    override fun correct(
        oldMemoryId: String,
        newClaim: String,
        sourceMessageIds: List<String>,
        timeMillis: Long,
    ): Memory {
        val old = require(oldMemoryId)
        check(old.status != MemoryStatus.CORRECTED) { "Tvrdnja je već ispravljena" }
        requireSources(sourceMessageIds)
        memoriesById[oldMemoryId] = old.copy(status = MemoryStatus.CORRECTED)
        // izričita ispravka korisnika odmah je potvrđena
        val replacement = Memory(
            nextId(), newClaim, sourceMessageIds, MemoryStatus.CONFIRMED, oldMemoryId, timeMillis,
        )
        memoriesById[replacement.id] = replacement
        return replacement
    }

    /** Briše tvrdnju, ali ne i poruke iz kojih je nastala. */
    override fun delete(memoryId: String) {
        memoriesById.remove(memoryId)
    }

    override fun memory(id: String): Memory? = memoriesById[id]

    override fun activeMemories(): List<Memory> =
        memoriesById.values.filter { it.status == MemoryStatus.CONFIRMED }

    override fun search(query: String): List<Memory> =
        activeMemories().filter { it.claim.contains(query, ignoreCase = true) }

    private fun require(id: String): Memory =
        memoriesById[id] ?: throw IllegalArgumentException("Nepoznata tvrdnja: $id")

    private fun requireSources(ids: List<String>) {
        require(ids.isNotEmpty()) { "Tvrdnja mora imati izvorne poruke" }
        val missing = ids.filter { it !in messagesById }
        require(missing.isEmpty()) { "Nepoznate izvorne poruke: $missing" }
    }
}
