package io.androllm.core.memory

import android.content.Context
import io.androllm.core.common.Result
import io.androllm.core.common.getOrNull
import io.androllm.core.common.isSuccess
import io.androllm.core.memory.context.ContextBuilder
import io.androllm.core.memory.db.dao.EmbeddingDao
import io.androllm.core.memory.db.dao.MemoryDao
import io.androllm.core.memory.db.dao.ProjectDao
import io.androllm.core.memory.db.dao.RelationshipDao
import io.androllm.core.memory.db.dao.SummaryDao
import io.androllm.core.memory.db.dao.TagDao
import io.androllm.core.memory.db.entity.MemoryEntity
import io.androllm.core.memory.embedding.EmbeddingProvider
import io.androllm.core.memory.intelligence.MemoryIntelligence
import io.androllm.core.memory.model.ExtractedMemory
import io.androllm.core.memory.model.MemoryExchange
import io.androllm.core.memory.model.MemorySearchFilters
import io.androllm.core.memory.model.MemorySettings
import io.androllm.core.memory.model.MemoryWriteAction
import io.androllm.core.memory.util.MemoryLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for the memory update/retrieval pipeline with mocked persistence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoryRepositoryTest {

    private val context: Context = mockk(relaxed = true)
    private val memoryDao: MemoryDao = mockk(relaxed = true)
    private val embeddingDao: EmbeddingDao = mockk(relaxed = true)
    private val summaryDao: SummaryDao = mockk(relaxed = true)
    private val projectDao: ProjectDao = mockk(relaxed = true)
    private val tagDao: TagDao = mockk(relaxed = true)
    private val relationshipDao: RelationshipDao = mockk(relaxed = true)
    private val embeddingProvider: EmbeddingProvider = mockk(relaxed = true)
    private val intelligence: MemoryIntelligence = mockk(relaxed = true)
    private val settingsStore: MemorySettingsStore = mockk(relaxed = true)

    private fun repo() = MemoryRepository(
        context = context,
        memoryDao = memoryDao,
        embeddingDao = embeddingDao,
        summaryDao = summaryDao,
        projectDao = projectDao,
        tagDao = tagDao,
        relationshipDao = relationshipDao,
        embeddingProvider = embeddingProvider,
        intelligence = intelligence,
        settingsStore = settingsStore,
        contextBuilder = ContextBuilder(),
        logger = MemoryLogger()
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { memoryDao.getFilteredIds(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { memoryDao.getByIds(any()) } returns emptyList()
        coEvery { embeddingDao.getAll() } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `processExchange is a no-op when memory disabled`() = runTest {
        coEvery { settingsStore.current() } returns MemorySettings(enabled = false)

        val summary = repo().processExchange(
            MemoryExchange("c1", "hi", "hello", emptyList(), 2)
        ).getOrNull()

        assertNotNull(summary)
        assertEquals(0, summary!!.inserted)
        coVerify(exactly = 0) { intelligence.extract(any(), any()) }
    }

    @Test
    fun `processExchange inserts extracted memories with embeddings`() = runTest {
        coEvery { settingsStore.current() } returns MemorySettings(
            enabled = true,
            extractionEnabled = true,
            embeddingModelPath = "/models/embed.gguf"
        )
        coEvery { intelligence.extract(any(), any()) } returns Result.Success(
            listOf(ExtractedMemory("User prefers dark mode", io.androllm.core.memory.MemoryCategory.PREFERENCES, 3, listOf("ui")))
        )
        coEvery { embeddingProvider.embed(any<String>()) } returns Result.Success(floatArrayOf(1f, 0f, 0f, 0f))
        coEvery { projectDao.getByName(any()) } returns null
        coEvery { tagDao.getByName(any()) } returns null

        val summary = repo().processExchange(
            MemoryExchange("c1", "I prefer dark mode", "Got it!", listOf("user" to "I prefer dark mode"), 2)
        ).getOrNull()

        assertNotNull(summary)
        assertEquals(1, summary!!.inserted)
        coVerify(exactly = 1) { embeddingDao.upsert(any()) }
        coVerify(exactly = 1) { memoryDao.upsert(any()) }
    }

    @Test
    fun `exact duplicate content updates instead of inserting`() = runTest {
        coEvery { settingsStore.current() } returns MemorySettings()

        val repo = repo()
        val first = repo.saveMemory(io.androllm.core.memory.MemoryCategory.PREFERENCES, "User prefers dark mode")
        assertTrue(first.isSuccess())
        assertEquals(MemoryWriteAction.INSERTED, first.getOrNull()?.action)
        val id = first.getOrNull()?.memoryId

        val existing = MemoryEntity(
            id = id!!,
            category = "PREFERENCES",
            content = "User prefers dark mode",
            createdAt = 0L,
            updatedAt = 0L
        )
        coEvery { memoryDao.getFilteredIds(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns listOf(id)
        coEvery { memoryDao.getByIds(any()) } returns listOf(existing)
        coEvery { memoryDao.getById(id) } returns existing
        coEvery { tagDao.getTagNamesForMemory(id) } returns emptyList()

        val second = repo.saveMemory(io.androllm.core.memory.MemoryCategory.PREFERENCES, "User prefers dark mode")
        assertEquals(MemoryWriteAction.UPDATED, second.getOrNull()?.action)
        coVerify(exactly = 1) { memoryDao.update(any()) }
    }

    @Test
    fun `retrieve falls back to keyword when no vector index`() = runTest {
        coEvery { settingsStore.current() } returns MemorySettings(enabled = true, retrievalCount = 5)
        coEvery { memoryDao.getFilteredIds(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns listOf("a", "b")
        coEvery { memoryDao.getByIds(any()) } returns listOf(
            MemoryEntity("a", "PREFERENCES", "User likes Kotlin", importance = 3, createdAt = 1L, updatedAt = 2L),
            MemoryEntity("b", "CUSTOM", "unrelated", createdAt = 1L, updatedAt = 1L)
        )
        coEvery { memoryDao.searchContentIds(any()) } returns listOf("a")
        coEvery { memoryDao.searchTagIds(any()) } returns emptyList()

        val results = repo().retrieve("kotlin").getOrNull().orEmpty()
        assertEquals("a", results.first().memory.id)
        assertTrue(results.first().matchedByKeyword)
    }

    @Test
    fun `retrieve returns empty when no candidates match filters`() = runTest {
        coEvery { settingsStore.current() } returns MemorySettings(enabled = true)
        coEvery { memoryDao.getFilteredIds(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns emptyList()

        val results = repo().retrieve("anything", MemorySearchFilters(projectId = "nope")).getOrNull().orEmpty()
        assertTrue(results.isEmpty())
    }

    @Test
    fun `inspector stats reflect stored counts`() = runTest {
        coEvery { settingsStore.current() } returns MemorySettings(enabled = true, retrievalCount = 7, similarityThreshold = 0.8f)
        coEvery { memoryDao.count() } returns 12
        coEvery { embeddingDao.count() } returns 10
        coEvery { projectDao.count() } returns 2
        coEvery { tagDao.count() } returns 5
        coEvery { summaryDao.count() } returns 1
        coEvery { relationshipDao.count() } returns 3

        val stats = repo().getInspectorStats()
        assertEquals(12, stats.memoryCount)
        assertEquals(10, stats.embeddingCount)
        assertEquals(7, stats.retrievalCount)
        assertEquals(0.8f, stats.similarityThreshold, 1e-6f)
        assertTrue(stats.enabled)
    }

    @Test
    fun `memories are stored without embeddings when no embedding source exists`() = runTest {
        coEvery { settingsStore.current() } returns MemorySettings(
            enabled = true,
            extractionEnabled = true
            // No local path, no cloud id ? embeddings skipped, memory still saved.
        )
        coEvery { intelligence.extract(any(), any()) } returns Result.Success(
            listOf(ExtractedMemory("User codes in Kotlin", io.androllm.core.memory.MemoryCategory.SKILLS, 2))
        )
        coEvery { projectDao.getByName(any()) } returns null
        coEvery { tagDao.getByName(any()) } returns null

        val summary = repo().processExchange(
            MemoryExchange("c1", "I code in Kotlin", "Great!", emptyList(), 1)
        ).getOrNull()

        assertNotNull(summary)
        assertEquals(1, summary!!.inserted)
        coVerify(exactly = 1) { memoryDao.upsert(any()) }
        coVerify(exactly = 0) { embeddingDao.upsert(any()) }
    }

    @Test
    fun `embedPendingMemories drains only memories missing vectors`() = runTest {
        coEvery { settingsStore.current() } returns MemorySettings(enabled = true, embeddingModelPath = "/models/embed.gguf")
        coEvery { memoryDao.getMemoryIdsWithoutEmbeddings() } returns listOf("m1", "m2")
        coEvery { memoryDao.getByIds(listOf("m1", "m2")) } returns listOf(
            MemoryEntity("m1", "PREFERENCES", "User likes tea", createdAt = 1L, updatedAt = 1L),
            MemoryEntity("m2", "CUSTOM", "Note", createdAt = 1L, updatedAt = 1L)
        )
        coEvery { embeddingProvider.embed(any<List<String>>()) } returns Result.Success(
            listOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f))
        )

        val count = repo().embedPendingMemories().getOrNull()

        assertEquals(2, count)
        coVerify(exactly = 1) { embeddingDao.upsertAll(any()) }
    }

    @Test
    fun `embedPendingMemories is a no-op without any embedding source`() = runTest {
        coEvery { settingsStore.current() } returns MemorySettings(enabled = true)

        val count = repo().embedPendingMemories().getOrNull()

        assertEquals(0, count)
        coVerify(exactly = 0) { memoryDao.getMemoryIdsWithoutEmbeddings() }
    }

    // ── Review workflow ──────────────────────────────────────────────────────

    @Test
    fun `extracted memories are inserted as SUGGESTED`() = runTest {
        coEvery { settingsStore.current() } returns MemorySettings(enabled = true, extractionEnabled = true)
        coEvery { intelligence.extract(any(), any()) } returns Result.Success(
            listOf(ExtractedMemory("User codes in Kotlin", io.androllm.core.memory.MemoryCategory.SKILLS, 2))
        )
        coEvery { projectDao.getByName(any()) } returns null
        coEvery { tagDao.getByName(any()) } returns null
        val saved = slot<MemoryEntity>()
        coEvery { memoryDao.upsert(capture(saved)) } returns Unit

        repo().processExchange(
            MemoryExchange("c1", "I code in Kotlin", "Great!", emptyList(), 1, sourceMessageId = "msg-7")
        )

        assertEquals("SUGGESTED", saved.captured.status)
        assertEquals("msg-7", saved.captured.sourceMessageId)
    }

    @Test
    fun `manually saved memories are inserted as CONFIRMED`() = runTest {
        coEvery { settingsStore.current() } returns MemorySettings(enabled = true)
        coEvery { projectDao.getByName(any()) } returns null
        coEvery { tagDao.getByName(any()) } returns null
        val saved = slot<MemoryEntity>()
        coEvery { memoryDao.upsert(capture(saved)) } returns Unit

        repo().saveMemory(io.androllm.core.memory.MemoryCategory.PREFERENCES, "User prefers dark mode")

        assertEquals("CONFIRMED", saved.captured.status)
    }

    @Test
    fun `suggestion never overwrites a confirmed memory`() = runTest {
        coEvery { settingsStore.current() } returns MemorySettings(enabled = true, extractionEnabled = true)
        coEvery { intelligence.extract(any(), any()) } returns Result.Success(
            listOf(ExtractedMemory("User prefers dark mode", io.androllm.core.memory.MemoryCategory.PREFERENCES, 3))
        )
        val existing = MemoryEntity("m1", "PREFERENCES", "User prefers dark mode", createdAt = 0L, updatedAt = 0L)
        coEvery { memoryDao.getFilteredIds(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns listOf("m1")
        coEvery { memoryDao.getByIds(any()) } returns listOf(existing)
        coEvery { memoryDao.getById("m1") } returns existing

        val summary = repo().processExchange(
            MemoryExchange("c1", "I prefer dark mode", "ok", emptyList(), 2)
        ).getOrNull()

        assertEquals(0, summary!!.updated)
        assertEquals(0, summary.inserted)
        coVerify(exactly = 0) { memoryDao.update(any()) }
        coVerify(exactly = 0) { memoryDao.upsert(any()) }
    }

    @Test
    fun `confirmMemory sets status CONFIRMED`() = runTest {
        val entity = MemoryEntity("m1", "CUSTOM", "User likes tea", createdAt = 0L, updatedAt = 0L, status = "SUGGESTED")
        coEvery { memoryDao.getById("m1") } returns entity

        val result = repo().confirmMemory("m1")

        assertTrue(result.isSuccess())
        coVerify(exactly = 1) { memoryDao.updateStatus("m1", "CONFIRMED", any()) }
    }

    @Test
    fun `rejectMemory deletes a suggestion but refuses a confirmed memory`() = runTest {
        coEvery { memoryDao.getById("s1") } returns
            MemoryEntity("s1", "CUSTOM", "User likes tea", createdAt = 0L, updatedAt = 0L, status = "SUGGESTED")
        coEvery { memoryDao.getById("c1") } returns
            MemoryEntity("c1", "CUSTOM", "User likes coffee", createdAt = 0L, updatedAt = 0L, status = "CONFIRMED")

        assertTrue(repo().rejectMemory("s1").isSuccess())
        coVerify(exactly = 1) { memoryDao.deleteById("s1") }

        assertFalse(repo().rejectMemory("c1").isSuccess())
        coVerify(exactly = 0) { memoryDao.deleteById("c1") }
    }

    @Test
    fun `correctMemory marks old CORRECTED and creates new CONFIRMED superseding it`() = runTest {
        coEvery { settingsStore.current() } returns MemorySettings(enabled = true)
        val old = MemoryEntity("old", "IDENTITY", "User name is Alice", createdAt = 0L, updatedAt = 0L, isPinned = true)
        coEvery { memoryDao.getById("old") } returns old
        coEvery { tagDao.getTagNamesForMemory("old") } returns emptyList()
        val created = slot<MemoryEntity>()
        val updatedOld = slot<MemoryEntity>()
        coEvery { memoryDao.upsert(capture(created)) } returns Unit
        coEvery { memoryDao.update(capture(updatedOld)) } returns Unit

        val result = repo().correctMemory("old", "  User name is Bob ")

        assertTrue(result.isSuccess())
        assertEquals("CONFIRMED", created.captured.status)
        assertEquals("old", created.captured.supersedesId)
        assertEquals("User name is Bob", created.captured.content)
        assertEquals("IDENTITY", created.captured.category)
        assertEquals(result.getOrNull()?.memoryId, created.captured.id)
        assertEquals("CORRECTED", updatedOld.captured.status)
        assertEquals("old", updatedOld.captured.id)
        assertFalse(updatedOld.captured.isPinned)
        coVerify(exactly = 1) { embeddingDao.deleteByMemoryId("old") }
    }

    @Test
    fun `correctMemory rejects blank content and unknown or already corrected memories`() = runTest {
        coEvery { settingsStore.current() } returns MemorySettings(enabled = true)
        coEvery { memoryDao.getById("m1") } returns
            MemoryEntity("m1", "CUSTOM", "x", createdAt = 0L, updatedAt = 0L)
        coEvery { memoryDao.getById("done") } returns
            MemoryEntity("done", "CUSTOM", "x", createdAt = 0L, updatedAt = 0L, status = "CORRECTED")
        coEvery { memoryDao.getById("missing") } returns null

        assertFalse(repo().correctMemory("m1", "   ").isSuccess())
        assertFalse(repo().correctMemory("done", "new text").isSuccess())
        assertFalse(repo().correctMemory("missing", "new text").isSuccess())
        coVerify(exactly = 0) { memoryDao.upsert(any()) }
    }

    @Test
    fun `updateContent edits text in place and keeps status`() = runTest {
        coEvery { settingsStore.current() } returns MemorySettings(enabled = true)
        coEvery { memoryDao.getById("m1") } returns
            MemoryEntity("m1", "CUSTOM", "User likes tea", createdAt = 0L, updatedAt = 0L, status = "SUGGESTED")
        val updated = slot<MemoryEntity>()
        coEvery { memoryDao.update(capture(updated)) } returns Unit

        val result = repo().updateContent("m1", "User likes green tea")

        assertTrue(result.isSuccess())
        assertEquals("User likes green tea", updated.captured.content)
        assertEquals("SUGGESTED", updated.captured.status)
    }

    @Test
    fun `retrieve excludes suggested and corrected memories from candidates`() = runTest {
        coEvery { settingsStore.current() } returns MemorySettings(enabled = true)
        coEvery { memoryDao.getFilteredIds(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            listOf("ok", "sug", "old")
        coEvery { memoryDao.getIdsWithStatus("SUGGESTED") } returns listOf("sug")
        coEvery { memoryDao.getIdsWithStatus("CORRECTED") } returns listOf("old")
        coEvery { memoryDao.searchContentIds(any()) } returns emptyList()
        coEvery { memoryDao.searchTagIds(any()) } returns emptyList()
        coEvery { memoryDao.getByIds(any()) } returns listOf(
            MemoryEntity("ok", "CUSTOM", "Confirmed note", createdAt = 1L, updatedAt = 1L)
        )

        val results = repo().retrieve("note").getOrNull().orEmpty()

        assertEquals(listOf("ok"), results.map { it.memory.id })
        coVerify { memoryDao.getByIds(listOf("ok")) }
    }
}
