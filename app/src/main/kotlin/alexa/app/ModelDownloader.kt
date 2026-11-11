package alexa.app

import alexa.model.CatalogEntry
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Preuzimanje modela s interneta. Piše u privremeni fajl, provjerava sažetak (ako postoji)
 * i tek onda preimenuje fajl. Prekinuto preuzimanje ne ostavlja pola modela.
 */
class ModelDownloader(private val dir: File) {

    /** Vraća preuzeti fajl ili baca grešku. [onProgress] dobija procenat 0–100. */
    fun download(entry: CatalogEntry, onProgress: (Int) -> Unit): File {
        dir.mkdirs()
        val target = File(dir, entry.fileName)
        val part = File(dir, entry.fileName + ".part")
        val connection = URL(entry.url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = true
            val code = connection.responseCode
            require(code in 200..299) { "Server je vratio grešku $code" }

            val total = connection.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        if (total > 0) onProgress((done * 100 / total).toInt())
                    }
                }
            }

            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            entry.sha256?.let { expected ->
                require(actual.equals(expected, ignoreCase = true)) { "Sažetak se ne poklapa, model nije sačuvan" }
            }
            if (target.exists()) target.delete()
            require(part.renameTo(target)) { "Ne mogu sačuvati model" }
            return target
        } catch (e: Exception) {
            part.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }
}
