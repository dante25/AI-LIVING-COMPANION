package io.androllm.core.packs

import io.androllm.core.common.MiniJson

object PackValidator {
    /** Provjerava cijeli paket (naziv fajla -> sadržaj). Ništa ne mijenja, samo daje presudu. */
    fun validate(entries: Map<String, ByteArray>): Verdict {
        if (entries.size > MAX_ENTRIES) return Verdict.Rejected("paket ima previše fajlova")
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
        if (manifest.formatVersion < 1) return Verdict.Rejected("neispravna verzija formata")
        if (manifest.type.lowercase() in FORBIDDEN_TYPES) {
            return Verdict.Rejected("identitet i memorija se ne pakuju kao sadržaj")
        }
        if (manifest.name.isBlank() || manifest.author.isBlank() || manifest.version.isBlank()) {
            return Verdict.Rejected("manifest mora imati name, author i version")
        }

        for (path in entries.keys) {
            if (path == "manifest.json") continue
            if (!isSafePath(path)) return Verdict.Rejected("nesiguran naziv fajla: $path")
            rejectName(path)?.let { return Verdict.Rejected(it) }
            if (path !in manifest.files) return Verdict.Rejected("fajl nije u manifestu: $path")
        }

        for ((path, sha) in manifest.files) {
            if (!isSafePath(path)) return Verdict.Rejected("nesiguran naziv fajla: $path")
            rejectName(path)?.let { return Verdict.Rejected(it) }
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

    private fun rejectName(path: String): String? {
        val ext = path.substringAfterLast('.', "").lowercase()
        if (ext in FORBIDDEN_EXTENSIONS) return "izvršni fajl nije dozvoljen: $path"
        if (path.substringAfterLast('/').lowercase() in FORBIDDEN_FILE_NAMES) {
            return "identitet, memorija i razgovori se ne pakuju: $path"
        }
        return null
    }

    private fun isJsonObject(bytes: ByteArray): Boolean =
        runCatching { MiniJson.parse(String(bytes, Charsets.UTF_8)) is Map<*, *> }.getOrDefault(false)
}
