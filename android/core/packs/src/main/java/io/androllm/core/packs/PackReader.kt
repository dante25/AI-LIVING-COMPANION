package io.androllm.core.packs

import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

object PackReader {
    /**
     * Čita zip u mapu naziv -> sadržaj uz zaštitu od prevelikih paketa (zip bomba):
     * ograničenja se provjeravaju tokom čitanja, ne nakon njega.
     */
    fun read(zip: ByteArray): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(zip.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.isDirectory) continue
                require(entry.name !in out) { "dupli fajl u paketu: ${entry.name}" }
                require(out.size < MAX_ENTRIES) { "paket ima previše fajlova" }
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var fileBytes = 0L
                while (true) {
                    val n = zis.read(chunk)
                    if (n < 0) break
                    total += n
                    fileBytes += n
                    require(fileBytes <= MAX_FILE_BYTES) { "fajl u paketu je prevelik" }
                    require(total <= MAX_TOTAL_BYTES) { "paket je prevelik" }
                    buffer.write(chunk, 0, n)
                }
                out[entry.name] = buffer.toByteArray()
            }
        }
        return out
    }
}
