package io.androllm.core.packs

import io.androllm.core.common.MiniJson
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object PackBuilder {
    /** Pravi .alexapack (zip) s ispravnim manifestom i sumama. Tip mora biti poznat tip sadržaja. */
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

        val manifest = linkedMapOf<String, Any?>(
            "formatVersion" to SUPPORTED_FORMAT,
            "type" to type,
            "name" to name,
            "author" to author,
            "version" to version,
            "license" to license,
        )
        if (voiceOrigin != null) manifest["voiceOrigin"] = voiceOrigin
        manifest["files"] = files.mapValues { (_, bytes) -> Integrity.sha256Hex(bytes) }

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(MiniJson.stringify(manifest).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            for ((path, bytes) in files) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
