package io.androllm.core.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.androllm.core.common.MiniJson
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * "Izvezi sve" / "Uvezi sve": JEDAN zip s manifestom (verzija formata, SHA-256 svakog dijela)
 * i po jednim `<key>.json` za svaki [BackupSection] (memory, conversations, settings, soul...).
 * Veliki fajlovi (modeli) se ne uključuju.
 *
 * Uvoz: cijela kopija se prvo provjeri (verzija, nazivi, sume, ispravan JSON) i tek onda se
 * dira bilo šta. Prije zamjene postojećih podataka pravi se sigurnosna kopija (snapshot) u
 * `filesDir/backups` i čuva se zadnja tri.
 */
@Singleton
class BackupManager(
    private val backupsDir: File,
    sections: Set<BackupSection>,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        sections: @JvmSuppressWildcards Set<BackupSection>,
    ) : this(File(context.filesDir, "backups"), sections, { System.currentTimeMillis() })

    private val sectionsByKey: Map<String, BackupSection>
    private val mutex = Mutex()

    init {
        val map = linkedMapOf<String, BackupSection>()
        for (s in sections.sortedBy { it.key }) {
            require(KEY_REGEX.matches(s.key)) { "Neispravan ključ dijela kopije: ${s.key}" }
            require(s.key !in map) { "Dva dijela kopije imaju isti ključ: ${s.key}" }
            map[s.key] = s
        }
        sectionsByKey = map
    }

    /** Ključevi dijelova koje ova verzija aplikacije izvozi. */
    fun sectionKeys(): List<String> = sectionsByKey.keys.toList()

    /** Piše jedan zip u [output] i zatvara ga. Ako bilo koji dio ne uspije, baca [BackupException]. */
    suspend fun exportAll(output: OutputStream) {
        mutex.withLock {
            val zip = buildZip()
            withContext(Dispatchers.IO) { output.use { it.write(zip) } }
        }
    }

    /** Isto što [exportAll], ali vraća bajtove (za testove i snapshot). */
    suspend fun exportToBytes(): ByteArray = mutex.withLock { buildZip() }

    /** Postojeće sigurnosne kopije prije uvoza, od najnovije. */
    fun listSnapshots(): List<File> =
        (backupsDir.listFiles { f -> f.isFile && SNAPSHOT_REGEX.matches(f.name) } ?: emptyArray<File>())
            .sortedByDescending { it.name }

    /** Uvozi zip. Ništa se ne mijenja dok cijela kopija nije provjerena i dok se ne napravi snapshot. */
    suspend fun importAll(input: InputStream): ImportReport = mutex.withLock {
        val parts = withContext(Dispatchers.IO) { readZip(input) }
        val plan = validate(parts)

        val snapshot = try {
            writeSnapshot(buildZip())
        } catch (e: Exception) {
            throw BackupException("Sigurnosna kopija postojećih podataka nije napravljena, uvoz je prekinut", e)
        }

        val imported = mutableListOf<String>()
        val failed = linkedMapOf<String, String>()
        for ((key, json) in plan.toImport) {
            try {
                sectionsByKey.getValue(key).import(json)
                imported += key
            } catch (e: Exception) {
                failed[key] = e.message ?: e.javaClass.simpleName
            }
        }
        ImportReport(imported, plan.ignored, failed, snapshot.name)
    }

    // ---------- izvoz ----------

    private suspend fun buildZip(): ByteArray {
        val contents = linkedMapOf<String, ByteArray>()
        for ((key, section) in sectionsByKey) {
            val json = try {
                section.export()
            } catch (e: Exception) {
                throw BackupException("Izvoz dijela '$key' nije uspio: ${e.message}", e)
            }
            contents[key] = json.toByteArray(Charsets.UTF_8)
        }
        val manifest = mapOf(
            "app" to "AndroLLM",
            "formatVersion" to BACKUP_FORMAT_VERSION,
            "createdAt" to clock(),
            "sections" to contents.map { (key, bytes) ->
                mapOf("key" to key, "file" to "$key.json", "sha256" to sha256(bytes))
            },
        )
        return withContext(Dispatchers.IO) {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zos ->
                zos.putNextEntry(ZipEntry(BACKUP_MANIFEST))
                zos.write(MiniJson.stringify(manifest).toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                for ((key, bytes) in contents) {
                    zos.putNextEntry(ZipEntry("$key.json"))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
            out.toByteArray()
        }
    }

    // ---------- uvoz ----------

    private class Plan(val toImport: List<Pair<String, String>>, val ignored: List<String>)

    private fun readZip(input: InputStream): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(input).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name
                    if (name.contains('/') || name.contains('\\') || name.contains("..")) {
                        throw BackupException("Kopija sadrži nesiguran naziv fajla: $name")
                    }
                    if (name in out) throw BackupException("Kopija sadrži dupli fajl: $name")
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    var size = 0L
                    while (true) {
                        val n = zis.read(chunk)
                        if (n < 0) break
                        size += n
                        total += n
                        if (size > BACKUP_MAX_ENTRY_BYTES || total > BACKUP_MAX_TOTAL_BYTES) {
                            throw BackupException("Kopija je prevelika")
                        }
                        buffer.write(chunk, 0, n)
                    }
                    out[name] = buffer.toByteArray()
                }
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("Fajl nije ispravna sigurnosna kopija", e)
        }
        return out
    }

    private fun validate(parts: Map<String, ByteArray>): Plan {
        val rawManifest = parts[BACKUP_MANIFEST] ?: throw BackupException("Fajl nije sigurnosna kopija (nema manifest.json)")
        val parsedManifest: Map<*, *>? = try {
            MiniJson.parse(String(rawManifest, Charsets.UTF_8)) as? Map<*, *>
        } catch (e: IllegalArgumentException) {
            null
        }
        val manifest = parsedManifest ?: throw BackupException("manifest.json nije ispravan")

        val version = manifest.int("formatVersion") ?: throw BackupException("manifest.json nema verziju formata")
        if (version > BACKUP_FORMAT_VERSION) {
            throw BackupException("Kopija je napravljena novijom verzijom aplikacije (format $version), ažuriraj aplikaciju")
        }
        if (version < 1) throw BackupException("Neispravna verzija formata kopije")

        val toImport = mutableListOf<Pair<String, String>>()
        val ignored = mutableListOf<String>()
        for (raw in manifest.list("sections").orEmpty()) {
            val entry = raw as? Map<*, *> ?: throw BackupException("manifest.json ima neispravan zapis dijela")
            val key = entry.str("key") ?: throw BackupException("manifest.json ima dio bez ključa")
            val file = entry.str("file") ?: "$key.json"
            val bytes = parts[file] ?: throw BackupException("U kopiji nedostaje fajl: $file")
            val expected = entry.str("sha256")
            if (expected != null && !expected.equals(sha256(bytes), ignoreCase = true)) {
                throw BackupException("Kontrolna suma ne odgovara za $file, kopija je oštećena")
            }
            val text = String(bytes, Charsets.UTF_8)
            try {
                MiniJson.parse(text)
            } catch (e: IllegalArgumentException) {
                throw BackupException("$file nije ispravan JSON")
            }
            if (key in sectionsByKey) toImport += key to text else ignored += key
        }
        return Plan(toImport, ignored)
    }

    // ---------- snapshot ----------

    /** Pravi sigurnosnu kopiju trenutnih podataka u filesDir/backups i čuva zadnja tri. */
    suspend fun createSnapshot(): File = mutex.withLock { writeSnapshot(buildZip()) }

    private fun writeSnapshot(zip: ByteArray): File {
        backupsDir.mkdirs()
        check(backupsDir.isDirectory) { "backups direktorij se ne može napraviti" }
        var n = 0
        var file: File
        do {
            file = File(backupsDir, String.format("snapshot-%015d-%02d.zip", clock(), n++))
        } while (file.exists())
        val tmp = File(backupsDir, file.name + ".tmp")
        tmp.writeBytes(zip)
        check(tmp.renameTo(file)) { "snapshot se ne može sačuvati" }
        listSnapshots().drop(BACKUP_KEEP_SNAPSHOTS).forEach { it.delete() }
        return file
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        val KEY_REGEX = Regex("[a-z0-9_-]+")
        val SNAPSHOT_REGEX = Regex("snapshot-\\d{15}-\\d{2}\\.zip")
    }
}
