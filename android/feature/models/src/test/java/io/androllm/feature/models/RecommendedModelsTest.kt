package io.androllm.feature.models

import io.androllm.core.models.DownloadStatus
import io.androllm.core.models.Model
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendedModelsTest {

    private val rec = RecommendedModels.all.first { it.id == "gemma-4-e2b" }

    private fun model(
        id: String = "x",
        url: String? = null,
        path: String? = null,
        downloaded: Boolean = false,
        status: DownloadStatus = DownloadStatus.NOT_DOWNLOADED
    ) = Model(
        id = id,
        name = "m",
        filePath = path,
        downloadUrl = url,
        isDownloaded = downloaded,
        downloadStatus = status
    )

    @Test
    fun `catalog ids are unique and files are litertlm`() {
        assertEquals(RecommendedModels.all.size, RecommendedModels.all.map { it.id }.toSet().size)
        assertTrue(RecommendedModels.all.all { it.fileName.endsWith(".litertlm") })
    }

    @Test
    fun `pickLiteRtFile prefers exact name case-insensitively`() {
        val files = listOf("README.md", "other.litertlm", rec.fileName.uppercase())
        assertEquals(rec.fileName.uppercase(), RecommendedModels.pickLiteRtFile(rec, files))
    }

    @Test
    fun `pickLiteRtFile falls back to first litertlm and prefers root level`() {
        val files = listOf("sub/a.litertlm", "b.litertlm", "c.litertlm", "x.gguf")
        assertEquals("b.litertlm", RecommendedModels.pickLiteRtFile(rec, files))
        assertEquals("sub/a.litertlm", RecommendedModels.pickLiteRtFile(rec, listOf("sub/a.litertlm")))
    }

    @Test
    fun `pickLiteRtFile returns null without litertlm`() {
        assertNull(RecommendedModels.pickLiteRtFile(rec, listOf("model.gguf", "README.md")))
        assertNull(RecommendedModels.pickLiteRtFile(rec, emptyList()))
    }

    @Test
    fun `matchInstalled matches by stable id url and file name`() {
        assertEquals("a", RecommendedModels.matchInstalled(rec, listOf(model(id = "a", url = RecommendedModels.downloadUrl(rec, "z.litertlm"))))?.id)
        assertEquals(RecommendedModels.modelId(rec), RecommendedModels.matchInstalled(rec, listOf(model(id = RecommendedModels.modelId(rec))))?.id)
        assertEquals("p", RecommendedModels.matchInstalled(rec, listOf(model(id = "p", path = "/data/models/${rec.fileName}")))?.id)
        assertNull(RecommendedModels.matchInstalled(rec, listOf(model(id = "o", url = "https://huggingface.co/other/repo/resolve/main/a.litertlm"))))
    }

    @Test
    fun `matchInstalled prefers downloaded over pending`() {
        val pending = model(id = RecommendedModels.modelId(rec), status = DownloadStatus.DOWNLOADING)
        val done = model(id = "done", url = RecommendedModels.downloadUrl(rec, rec.fileName), downloaded = true)
        assertEquals("done", RecommendedModels.matchInstalled(rec, listOf(pending, done))?.id)
    }

    @Test
    fun `statusOf combines persisted and live state`() {
        assertEquals(RecommendedStatus.AVAILABLE, RecommendedModels.statusOf(null, null))
        assertEquals(RecommendedStatus.DOWNLOADED, RecommendedModels.statusOf(model(downloaded = true), DownloadStatus.DOWNLOADING))
        val pending = model(status = DownloadStatus.DOWNLOADING)
        assertEquals(RecommendedStatus.DOWNLOADING, RecommendedModels.statusOf(pending, null))
        assertEquals(RecommendedStatus.PAUSED, RecommendedModels.statusOf(pending, DownloadStatus.PAUSED))
        assertEquals(RecommendedStatus.FAILED, RecommendedModels.statusOf(pending, DownloadStatus.ERROR))
        assertEquals(RecommendedStatus.DOWNLOADED, RecommendedModels.statusOf(pending, DownloadStatus.DOWNLOADED))
        assertEquals(RecommendedStatus.DOWNLOADING, RecommendedModels.statusOf(pending, DownloadStatus.NOT_DOWNLOADED))
    }

    @Test
    fun `isTooHeavy tolerates reserved memory`() {
        assertFalse(RecommendedModels.isTooHeavy(rec, null))
        assertFalse(RecommendedModels.isTooHeavy(rec, 5.5f))
        assertTrue(RecommendedModels.isTooHeavy(rec, 4f))
    }

    @Test
    fun `formatSizeMb formats MB and GB`() {
        assertEquals("2,6 GB", RecommendedModels.formatSizeMb(2600))
        assertEquals("800 MB", RecommendedModels.formatSizeMb(800))
    }

    @Test
    fun `toModel builds a litertlm download model with HF url`() {
        val m = RecommendedModels.toModel(rec, "f.litertlm", nowMs = 1L)
        assertEquals(RecommendedModels.modelId(rec), m.id)
        assertEquals("https://huggingface.co/${rec.repo}/resolve/main/f.litertlm", m.downloadUrl)
        assertFalse(m.isDownloaded)
    }
}
