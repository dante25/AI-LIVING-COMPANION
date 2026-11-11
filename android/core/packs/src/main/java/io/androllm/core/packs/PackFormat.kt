package io.androllm.core.packs

import io.androllm.core.common.MiniJson
import java.security.MessageDigest

/**
 * Paketi sadržaja (.alexapack = zip s manifest.json): odjeća, glasovi, animacije i osobnosti.
 *
 * Pravila:
 *  - paketi su samo podaci, nikad izvršni kod;
 *  - manifest se provjerava, svaki fajl ima SHA-256 sumu, a svi fajlovi moraju biti u manifestu;
 *  - identitet, memorija i razgovori se ne pakuju (to je zaseban "Izvezi sve" izvoz u core:backup);
 *  - glas mora imati poznat izvor: sintetički, licencirani ili uz pristanak;
 *  - verzija formata se provjerava da stari paketi ne pucaju;
 *  - nepoznat tip se čuva, ali je neaktivan.
 */
const val SUPPORTED_FORMAT = 1
const val MAX_FILE_BYTES = 64L * 1024 * 1024
const val MAX_TOTAL_BYTES = 128L * 1024 * 1024
const val MAX_ENTRIES = 2000
const val PACK_EXTENSION = "alexapack"

val CONTENT_TYPES: Set<String> = setOf("outfit", "voice", "animation", "personality")
internal val FORBIDDEN_TYPES: Set<String> =
    setOf("memory", "memories", "identity", "conversation", "conversations", "soul")
internal val FORBIDDEN_FILE_NAMES: Set<String> =
    setOf("memory.json", "conversations.json", "soul.json", "settings.json", "identity.json")
internal val FORBIDDEN_EXTENSIONS: Set<String> = setOf(
    "dex", "odex", "jar", "aar", "class", "so", "dylib", "exe", "dll", "msi", "sh", "bat", "cmd",
    "ps1", "vbs", "js", "mjs", "py", "wasm", "apk",
)
internal val VOICE_ORIGINS: Set<String> = setOf("synthetic", "licensed", "consented")

data class Manifest(
    val formatVersion: Int,
    val type: String,
    val name: String,
    val author: String,
    val version: String,
    val license: String,
    val files: Map<String, String>,
    val voiceOrigin: String?,
) {
    /** Stabilan identifikator paketa; ponovni uvoz istog paketa ga ne umnožava. */
    val id: String get() = "$author/$name@$version"
}

sealed interface Verdict {
    data class Accepted(val manifest: Manifest) : Verdict
    data class Preserved(val manifest: Manifest) : Verdict
    data class Rejected(val reason: String) : Verdict
}

object Integrity {
    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

fun isSafePath(path: String): Boolean {
    if (path.isEmpty() || path.startsWith("/") || path.contains('\\') || path.contains(':')) return false
    if (path.any { it < ' ' }) return false
    return path.split('/').none { it.isEmpty() || it == ".." || it == "." }
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
            (k as? String ?: throw IllegalArgumentException("naziv fajla nije tekst")) to
                (v as? String ?: throw IllegalArgumentException("sažetak za $k nije tekst"))
        },
        voiceOrigin = obj["voiceOrigin"] as? String,
    )
}
