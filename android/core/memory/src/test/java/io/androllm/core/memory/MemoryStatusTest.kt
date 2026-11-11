package io.androllm.core.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryStatusTest {

    @Test
    fun `fromName parses known statuses case-insensitively`() {
        assertEquals(MemoryStatus.SUGGESTED, MemoryStatus.fromName("suggested"))
        assertEquals(MemoryStatus.CONFIRMED, MemoryStatus.fromName(" CONFIRMED "))
        assertEquals(MemoryStatus.CORRECTED, MemoryStatus.fromName("Corrected"))
    }

    @Test
    fun `unknown blank or null status is treated as CONFIRMED`() {
        assertEquals(MemoryStatus.CONFIRMED, MemoryStatus.fromName(null))
        assertEquals(MemoryStatus.CONFIRMED, MemoryStatus.fromName(""))
        assertEquals(MemoryStatus.CONFIRMED, MemoryStatus.fromName("something-else"))
    }

    @Test
    fun `relationship categories are parsed and flagged`() {
        assertEquals(MemoryCategory.RELATIONSHIP_MOMENT, MemoryCategory.fromName("relationship_moment"))
        assertEquals(MemoryCategory.RELATIONSHIP_CONFLICT, MemoryCategory.fromName("Relationship Conflict"))
        assertEquals(MemoryCategory.RELATIONSHIP_DISLIKE, MemoryCategory.fromName("RELATIONSHIP_DISLIKE"))
        assertTrue(MemoryCategory.RELATIONSHIP_MOMENT.isRelationship)
        assertFalse(MemoryCategory.PREFERENCES.isRelationship)
    }
}
