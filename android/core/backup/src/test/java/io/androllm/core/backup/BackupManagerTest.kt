package io.androllm.core.backup

import io.androllm.core.common.MiniJson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Lažni dio kopije: lista poruka po ID-u, pa ponovljen uvoz ne smije umnožiti stavke. */
private class FakeSection(override val key: String, initial: Map<String, String> = emptyMap()) : BackupSection {
    val items = linkedMapOf<String, String>().apply { putAll(initial) }
    var failImport = false

    override suspend fun export(): String = MiniJson.stringify(mapOf("items" to items))

    override suspend fun import(json: String) {
        if (failImport) throw IllegalStateException("namjerna greška")
        val map = (MiniJson.parse(json) as Map<*, *>)["items"] as Map<*, *>
        for ((k, v) in map) items[k as String] = v as String
    }
}

class BackupManagerTest {
    private lateinit var dir: File
    private var now = 1_700_000_000_000L

    @Before
    fun setUp() {
        dir = File.createTempFile("backup", "").apply { delete(); mkdirs() }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun manager(vararg sections: BackupSection) =
        BackupManager(File(dir, "backups"), sections.toSet(), clock = { now++ })

    private fun entries(zip: ByteArray): Map<String, String> {
        val out = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(zip)).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                out[e.name] = String(zis.readBytes(), Charsets.UTF_8)
            }
        }
        return out
    }

    private fun zipOf(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            for ((name, text) in files) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(text.toByteArray())
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun exportContainsManifestAndAllSections() = runBlocking {
        val m = manager(FakeSection("memory", mapOf("a" to "1")), FakeSection("soul"))
        val files = entries(m.exportToBytes())
        assertEquals(setOf("manifest.json", "memory.json", "soul.json"), files.keys)
        val manifest = MiniJson.parse(files.getValue("manifest.json")) as Map<*, *>
        assertEquals(1.0, manifest["formatVersion"])
        assertEquals(2, (manifest["sections"] as List<*>).size)
    }

    @Test
    fun importOnCleanInstallRestoresEverything() = runBlocking {
        val source = FakeSection("conversations", mapOf("m1" to "zdravo", "m2" to "ćao"))
        val zip = manager(source).exportToBytes()

        val target = FakeSection("conversations")
        val report = manager(target).importAll(ByteArrayInputStream(zip))
        assertEquals(source.items, target.items)
        assertEquals(listOf("conversations"), report.imported)
        assertTrue(report.failed.isEmpty())
    }

    @Test
    fun repeatedImportDoesNotDuplicate() = runBlocking {
        val source = FakeSection("conversations", mapOf("m1" to "a", "m2" to "b"))
        val zip = manager(source).exportToBytes()
        val target = FakeSection("conversations")
        val m = manager(target)
        m.importAll(ByteArrayInputStream(zip))
        m.importAll(ByteArrayInputStream(zip))
        assertEquals(2, target.items.size)
    }

    @Test
    fun snapshotIsCreatedBeforeImportAndOnlyLastThreeKept() = runBlocking {
        val target = FakeSection("memory", mapOf("stara" to "vrijednost"))
        val m = manager(target)
        val zip = manager(FakeSection("memory", mapOf("nova" to "x"))).exportToBytes()

        val first = m.importAll(ByteArrayInputStream(zip))
        val snapshotContent = entries(File(File(dir, "backups"), first.snapshotName).readBytes())
        assertTrue(snapshotContent.getValue("memory.json").contains("stara"))
        assertFalse(snapshotContent.getValue("memory.json").contains("nova"))

        repeat(4) { m.importAll(ByteArrayInputStream(zip)) }
        assertEquals(3, m.listSnapshots().size)
    }

    @Test
    fun failedSectionDoesNotStopOthers() = runBlocking {
        val zip = manager(FakeSection("a", mapOf("k" to "v")), FakeSection("b", mapOf("k" to "v"))).exportToBytes()
        val a = FakeSection("a").apply { failImport = true }
        val b = FakeSection("b")
        val report = manager(a, b).importAll(ByteArrayInputStream(zip))
        assertEquals(listOf("b"), report.imported)
        assertEquals(setOf("a"), report.failed.keys)
        assertEquals(1, b.items.size)
    }

    @Test
    fun unknownSectionIsIgnored() = runBlocking {
        val zip = manager(FakeSection("buducnost", mapOf("k" to "v")), FakeSection("memory")).exportToBytes()
        val memory = FakeSection("memory")
        val report = manager(memory).importAll(ByteArrayInputStream(zip))
        assertEquals(listOf("buducnost"), report.ignored)
    }

    @Test
    fun newerFormatIsRejectedAndNothingChanges() {
        val zip = zipOf("manifest.json" to "{\"formatVersion\":99,\"sections\":[]}")
        val target = FakeSection("memory", mapOf("x" to "y"))
        val m = manager(target)
        assertThrows(BackupException::class.java) { runBlocking { m.importAll(ByteArrayInputStream(zip)) } }
        assertTrue(m.listSnapshots().isEmpty())
        assertEquals(1, target.items.size)
    }

    @Test
    fun tamperedSectionIsRejected() {
        val good = runBlocking { manager(FakeSection("memory", mapOf("k" to "v"))).exportToBytes() }
        val files = entries(good).toMutableMap()
        files["memory.json"] = "{\"items\":{\"k\":\"zlo\"}}"
        val zip = zipOf(*files.map { it.key to it.value }.toTypedArray())
        val target = FakeSection("memory")
        val m = manager(target)
        assertThrows(BackupException::class.java) { runBlocking { m.importAll(ByteArrayInputStream(zip)) } }
        assertTrue(target.items.isEmpty())
    }

    @Test
    fun unsafeEntryNameIsRejected() {
        val zip = zipOf("manifest.json" to "{}", "../evil.json" to "{}")
        val m = manager(FakeSection("memory"))
        assertThrows(BackupException::class.java) { runBlocking { m.importAll(ByteArrayInputStream(zip)) } }
    }

    @Test
    fun notAZipIsRejected() {
        val m = manager(FakeSection("memory"))
        assertThrows(BackupException::class.java) {
            runBlocking { m.importAll(ByteArrayInputStream("nije zip".toByteArray())) }
        }
    }

    @Test
    fun duplicateSectionKeysAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupManager(File(dir, "b"), setOf(FakeSection("x"), FakeSection("x")))
        }
    }
}
