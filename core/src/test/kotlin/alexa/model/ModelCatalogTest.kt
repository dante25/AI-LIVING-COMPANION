package alexa.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelCatalogTest {
    @Test
    fun idsAreUnique() {
        val ids = ModelCatalog.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun downloadsUseHttpsAndGgufFiles() {
        for (e in ModelCatalog.entries) {
            assertTrue(e.url.startsWith("https://"), e.id)
            assertTrue(e.fileName.endsWith(".gguf"), e.id)
            assertTrue(e.url.endsWith(e.fileName), e.id)
        }
    }

    @Test
    fun catalogHasWeakAndStrongTiers() {
        val tiers = ModelCatalog.entries.map { it.tier }.toSet()
        assertTrue(Tier.WEAK in tiers)
        assertTrue(Tier.STRONG in tiers)
    }
}
