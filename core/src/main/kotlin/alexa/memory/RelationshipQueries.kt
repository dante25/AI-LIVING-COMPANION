package alexa.memory

/** Odnos: zajedničke trenutke, svađe i ono što joj smeta. Ulazi u kontekst kao poseban dio. */
fun MemoryStore.relationshipMemories(): List<Memory> =
    activeMemories().filter {
        it.kind == MemoryKind.SHARED_MOMENT || it.kind == MemoryKind.CONFLICT || it.kind == MemoryKind.DISLIKE
    }
