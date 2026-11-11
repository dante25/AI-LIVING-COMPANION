package io.androllm.core.packs

import io.androllm.core.common.MiniJson
import java.io.File

/**
 * Spisak instaliranih paketa. Sa [root] != null paketi se trajno čuvaju na disku
 * (u aplikaciji: filesDir/packs), inače su samo u memoriji (testovi).
 *
 * Raspored na disku: `root/<sha256(id)>/content/<fajlovi paketa>` i `root/<sha256(id)>/state.json`
 * (id i aktivnost). Svaki paket je prije upisa u cijelosti validiran, pa odbijen paket ne mijenja ništa.
 */
class ContentStore(private val root: File? = null) {
    data class Installed(val id: String, val manifest: Manifest, val active: Boolean)

    private val packs = linkedMapOf<String, Installed>()

    init {
        root?.let { loadFromDisk(it) }
    }

    @Synchronized
    fun list(): List<Installed> = packs.values.toList()

    /** Provjerava cijeli paket prije bilo kakve promjene. Ponovni uvoz ne umnožava zapis. */
    @Synchronized
    fun importEntries(entries: Map<String, ByteArray>): Verdict {
        val verdict = PackValidator.validate(entries)
        when (verdict) {
            is Verdict.Accepted -> store(verdict.manifest, entries, active = true)
            is Verdict.Preserved -> store(verdict.manifest, entries, active = false)
            is Verdict.Rejected -> Unit
        }
        return verdict
    }

    fun importZip(zip: ByteArray): Verdict {
        val entries = try {
            PackReader.read(zip)
        } catch (e: Exception) {
            return Verdict.Rejected(e.message ?: "paket se ne može pročitati")
        }
        return importEntries(entries)
    }

    /** Neaktivan (nepoznat tip) paket se ne može aktivirati. */
    @Synchronized
    fun setActive(id: String, active: Boolean) {
        val current = packs[id] ?: throw IllegalArgumentException("Nepoznat paket: $id")
        if (active) {
            check(current.manifest.type in CONTENT_TYPES) { "Nepoznat tip paketa se ne može aktivirati" }
        }
        packs[id] = current.copy(active = active)
        root?.let { writeState(dirFor(it, id), id, active) }
    }

    /** Briše paket (i sa diska). */
    @Synchronized
    fun remove(id: String) {
        if (packs.remove(id) != null) root?.let { dirFor(it, id).deleteRecursively() }
    }

    /** Sadržaj jednog fajla instaliranog paketa (samo trajna pohrana), ili null. */
    @Synchronized
    fun readFile(id: String, path: String): ByteArray? {
        val r = root ?: return null
        if (id !in packs || !isSafePath(path)) return null
        val f = File(File(dirFor(r, id), "content"), path)
        return if (f.isFile) f.readBytes() else null
    }

    private fun store(manifest: Manifest, entries: Map<String, ByteArray>, active: Boolean) {
        val id = manifest.id
        if (id in packs) return
        root?.let { r ->
            val dir = dirFor(r, id)
            val tmp = File(r, dir.name + ".tmp")
            tmp.deleteRecursively()
            val content = File(tmp, "content")
            for ((path, bytes) in entries) {
                val target = File(content, path)
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
            }
            writeState(tmp, id, active)
            dir.deleteRecursively()
            check(tmp.renameTo(dir)) { "paket se ne može sačuvati" }
        }
        packs[id] = Installed(id, manifest, active)
    }

    private fun dirFor(r: File, id: String): File =
        File(r, Integrity.sha256Hex(id.toByteArray(Charsets.UTF_8)))

    private fun writeState(dir: File, id: String, active: Boolean) {
        dir.mkdirs()
        File(dir, "state.json").writeText(MiniJson.stringify(mapOf("id" to id, "active" to active)))
    }

    private fun loadFromDisk(r: File) {
        val dirs = r.listFiles { f -> f.isDirectory && !f.name.endsWith(".tmp") } ?: return
        for (dir in dirs.sortedBy { it.name }) {
            try {
                val state = MiniJson.parse(File(dir, "state.json").readText()) as Map<*, *>
                val manifest = parseManifest(File(File(dir, "content"), "manifest.json").readText())
                val id = state["id"] as? String ?: continue
                if (id != manifest.id) continue
                val active = (state["active"] as? Boolean ?: false) && manifest.type in CONTENT_TYPES
                packs[id] = Installed(id, manifest, active)
            } catch (e: Exception) {
                // Oštećen zapis se preskače; ne ruši aplikaciju.
            }
        }
    }
}
