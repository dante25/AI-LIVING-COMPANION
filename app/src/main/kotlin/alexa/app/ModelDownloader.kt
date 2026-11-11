package alexa.app

import alexa.model.CatalogEntry
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest

/**
 * Preuzimanje modela preko sistemskog DownloadManager-a. Preuzimanje radi u sistemu,
 * pa rotacija ekrana, izlazak iz ekrana ili zatvaranje aplikacije ga ne prekidaju.
 * Napredak se vidi u obavijestima i u katalogu.
 */
object ModelDownloads {
    private const val PREFS = "model-downloads"

    /** Mapa u kojoj žive modeli: app-specifičan spremnik, bez posebnih dozvola. */
    fun modelsDir(context: Context): File =
        context.getExternalFilesDir("models") ?: File(context.filesDir, "models")

    fun enqueue(context: Context, entry: CatalogEntry) {
        val request = DownloadManager.Request(Uri.parse(entry.url))
            .setTitle(entry.name)
            .setDescription("Mini Alexa: preuzimanje modela")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, null, "models/${entry.fileName}")
        val id = manager(context).enqueue(request)
        prefs(context).edit().putLong(entry.id, id).apply()
    }

    /** Stanje preuzimanja koje je u toku, ili null ako nije. */
    fun state(context: Context, entryId: String): DownloadState? {
        val id = idOf(context, entryId)
        if (id < 0) return null
        manager(context).query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) return null
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val percent = if (total > 0) (done * 100 / total).toInt() else 0
            return DownloadState(status, percent)
        }
    }

    fun idOf(context: Context, entryId: String): Long = prefs(context).getLong(entryId, -1L)

    fun statusOf(context: Context, downloadId: Long): Int? {
        manager(context).query(DownloadManager.Query().setFilterById(downloadId)).use { c ->
            if (!c.moveToFirst()) return null
            return c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        }
    }

    fun forget(context: Context, entryId: String) {
        prefs(context).edit().remove(entryId).apply()
    }

    /** SHA-256 fajla, čitan u dijelovima (modeli su veliki). */
    fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun manager(context: Context): DownloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

data class DownloadState(val status: Int, val percent: Int)
