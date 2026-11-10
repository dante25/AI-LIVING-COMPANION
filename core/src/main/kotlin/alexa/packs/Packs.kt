package alexa.packs

import alexa.model.Integrity
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Paketi sadržaja: odjeća, glasovi, animacije i osobnosti.
 *
 * Pravila (iz razgovora i plana):
 *  - paketi su samo podaci, nikad izvršni kod;
 *  - manifest se provjerava, svaki fajl ima kontrolnu sumu, a svi fajlovi moraju biti u manifestu;
 *  - memorija, razgovori i identitet se ne pakuju (to je zaseban, šifrovan export);
 *  - glas mora imati poznat izvor: sintetički, licencirani ili uz pristanak;
 *  - verzija formata se provjerava da stari paketi ne pucaju;
 *  - nepoznat tip se čuva, ali je neaktivan.
 */
const val SUPPORTED_FORMAT = 1
const val MAX_FILE_BYTES = 128L * 1024 * 1024
const val MAX_TOTAL_BYTES = 512L * 1024 * 1024

val CONTENT_TYPES: Set<String> = setOf("outfit", "voice", "animation", "personality")
private val FORBIDDEN_TYPES = setOf("memory", "identity", "conversation")
private val FORBIDDEN_EXTENSIONS = setOf("dex", "jar", "class", "so", "exe", "dll", "sh", "bat", "ps1", "js", "apk")
private val VOICE_ORIGINS = setOf("synthetic", "licensed", "consented")

data class Manifest(
    val formatVersion: Int,
    val type: String,
    val name: String,
    val author: String,
    val version: String,
    val license: String,
    val files: Map<String, String>,
    val voiceOrigin: String?,
)

sealed interface Verdict {
    data class Accepted(val manifest: Manifest) : Verdict
    data class Preserved(val manifest: Manifest) : Verdict
    data class Rejected(val reason: String) : Verdict
}

fun isSafePath(path: String): Boolean {
    if (path.isEmpty() || path.startsWith("/") || path.contains('\\') || path.contains(':')) return false
    return path.split('/').none { it.isEmpty() || it == ".." }
}

fun parseManifest(text: String): Manifest {
    val obj = MiniJson.parse(text) as? Map<*, *> ?: throw IllegalArgumentException("manifest nije objekat")
    fun str(key: String): String = obj[key] as? String ?: throw IllegalArgumentException("nedostaje $key")
    val format = (obj["formatVersion"] as? Double)?.toInt() ?: throw IllegalArgumentException("nedostaje formatVersion")
    val files = obj["files"] as? Map<*, *> ?: throw IllegalArgumentException("nedostaje files")
    return Manifest(
        formatVersion = format,
        type = str("type"),
        name = str("name"),
        author = str("author"),
        version = str("version"),
        license = str("license"),
        files = files.entries.associate { (k, v) ->
            (k as String) to (v as? String ?: throw IllegalArgumentException("sažetak za $k nije tekst"))
        },
        voiceOrigin = obj["voiceOrigin"] as? String,
    )
}

object PackValidator {
    fun validate(entries: Map<String, ByteArray>): Verdict {
        if (entries.values.sumOf { it.size.toLong() } > MAX_TOTAL_BYTES) return Verdict.Rejected("paket je prevelik")
        if (entries.values.any { it.size > MAX_FILE_BYTES }) return Verdict.Rejected("fajl u paketu je prevelik")

        val raw = entries["manifest.json"] ?: return Verdict.Rejected("nedostaje manifest.json")
        val manifest = try {
            parseManifest(String(raw, Charsets.UTF_8))
        } catch (e: IllegalArgumentException) {
            return Verdict.Rejected("neispravan manifest: ${e.message}")
        }

        if (manifest.formatVersion > SUPPORTED_FORMAT) {
            return Verdict.Rejected("format ${manifest.formatVersion} nije podržan")
        }
        if (manifest.type in FORBIDDEN_TYPES) {
            return Verdict.Rejected("identitet i memorija se ne pakuju kao sadržaj")
        }

        for (path in entries.keys) {
            if (path == "manifest.json") continue
            if (!isSafePath(path)) return Verdict.Rejected("nesiguran naziv fajla: $path")
            if (path.substringAfterLast('.', "").lowercase() in FORBIDDEN_EXTENSIONS) {
                return Verdict.Rejected("izvršni fajl nije dozvoljen: $path")
            }
            if (path !in manifest.files) return Verdict.Rejected("fajl nije u manifestu: $path")
        }

        for ((path, sha) in manifest.files) {
            if (!isSafePath(path)) return Verdict.Rejected("nesiguran naziv fajla: $path")
            val bytes = entries[path] ?: return Verdict.Rejected("nedostaje fajl: $path")
            if (Integrity.sha256Hex(bytes) != sha.lowercase()) {
                return Verdict.Rejected("kontrolna suma ne odgovara: $path")
            }
        }

        val names = manifest.files.keys
        return when (manifest.type) {
            "personality" -> when {
                "personality.json" !in names -> Verdict.Rejected("osobnost mora imati personality.json")
                !isJsonObject(entries.getValue("personality.json")) ->
                    Verdict.Rejected("personality.json nije ispravan JSON objekat")
                else -> Verdict.Accepted(manifest)
            }
            "outfit" -> if (names.none { it.endsWith(".vrm") }) {
                Verdict.Rejected("odjeća mora imati .vrm model")
            } else {
                Verdict.Accepted(manifest)
            }
            "voice" -> when {
                names.none { it.endsWith(".onnx") } -> Verdict.Rejected("glas mora imati .onnx model")
                manifest.voiceOrigin !in VOICE_ORIGINS ->
                    Verdict.Rejected("glas nema poznat izvor (synthetic, licensed ili consented)")
                else -> Verdict.Accepted(manifest)
            }
            "animation" -> if (names.none { it.endsWith(".anim.json") }) {
                Verdict.Rejected("animacija mora imati .anim.json")
            } else {
                Verdict.Accepted(manifest)
            }
            else -> Verdict.Preserved(manifest)
        }
    }

    private fun isJsonObject(bytes: ByteArray): Boolean =
        runCatching { MiniJson.parse(String(bytes, Charsets.UTF_8)) is Map<*, *> }.getOrDefault(false)
}

object PackReader {
    fun read(zip: ByteArray): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(zip.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.isDirectory) continue
                require(entry.name !in out) { "dupli fajl u paketu: ${entry.name}" }
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val n = zis.read(chunk)
                    if (n < 0) break
                    total += n
                    require(total <= MAX_TOTAL_BYTES) { "paket je prevelik" }
                    buffer.write(chunk, 0, n)
                }
                out[entry.name] = buffer.toByteArray()
            }
        }
        return out
    }
}

class ContentStore {
    data class Installed(val id: String, val manifest: Manifest, val active: Boolean)

    private val packs = linkedMapOf<String, Installed>()

    fun list(): List<Installed> = packs.values.toList()

    /** Provjerava cijeli paket prije bilo kakve promjene. Ponovni uvoz ne umnožava zapis. */
    fun importEntries(entries: Map<String, ByteArray>): Verdict {
        val verdict = PackValidator.validate(entries)
        when (verdict) {
            is Verdict.Accepted -> store(verdict.manifest, active = true)
            is Verdict.Preserved -> store(verdict.manifest, active = false)
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
    fun setActive(id: String, active: Boolean) {
        val current = packs[id] ?: throw IllegalArgumentException("Nepoznat paket: $id")
        if (active) {
            check(current.manifest.type in CONTENT_TYPES) { "Nepoznat tip paketa se ne može aktivirati" }
        }
        packs[id] = current.copy(active = active)
    }

    private fun store(manifest: Manifest, active: Boolean) {
        val id = "${manifest.author}/${manifest.name}@${manifest.version}"
        if (id !in packs) packs[id] = Installed(id, manifest, active)
    }
}

object PackBuilder {
    fun build(
        type: String,
        name: String,
        author: String,
        version: String,
        license: String,
        voiceOrigin: String?,
        files: Map<String, ByteArray>,
    ): ByteArray {
        require(type in CONTENT_TYPES) { "tip '$type' se ne može izvesti kao sadržaj" }
        require(files.keys.all(::isSafePath)) { "nesiguran naziv fajla" }

        val fileEntries = files.entries.joinToString(",") { (path, bytes) ->
            "${quote(path)}:${quote(Integrity.sha256Hex(bytes))}"
        }
        val origin = voiceOrigin?.let { ",\"voiceOrigin\":${quote(it)}" } ?: ""
        val manifest = "{\"formatVersion\":$SUPPORTED_FORMAT,\"type\":${quote(type)}," +
            "\"name\":${quote(name)},\"author\":${quote(author)},\"version\":${quote(version)}," +
            "\"license\":${quote(license)}$origin,\"files\":{$fileEntries}}"

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifest.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            for ((path, bytes) in files) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun quote(s: String): String {
        val escaped = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
        return "\"$escaped\""
    }
}
