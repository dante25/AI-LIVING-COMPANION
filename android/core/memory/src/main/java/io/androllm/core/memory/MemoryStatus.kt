package io.androllm.core.memory

/**
 * Review status of a memory. Stored as a stable enum name in the database.
 *
 * - [SUGGESTED]: extracted automatically, waiting for the user. NEVER injected into prompts.
 * - [CONFIRMED]: approved (or entered/imported by the user). The only status used for prompt context.
 * - [CORRECTED]: replaced by a newer memory (see `supersedesId` on the replacement). Kept as history only.
 */
enum class MemoryStatus {
    SUGGESTED,
    CONFIRMED,
    CORRECTED;

    companion object {
        /**
         * Parses a stored/exported status. Unknown or blank values map to [CONFIRMED]
         * so rows created before statuses existed keep working as confirmed memories.
         */
        fun fromName(raw: String?): MemoryStatus {
            if (raw.isNullOrBlank()) return CONFIRMED
            val normalized = raw.trim().uppercase()
            return entries.firstOrNull { it.name == normalized } ?: CONFIRMED
        }
    }
}
