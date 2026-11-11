package alexa.app

import alexa.model.ModelCatalog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import java.io.File

/**
 * Prima javu da je preuzimanje završeno. Provjerava status i sažetak (ako je poznat).
 * Neispravan fajl se briše, da se ne ostavi model koji nije potvrđen.
 */
class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        val entry = ModelCatalog.entries.firstOrNull { ModelDownloads.idOf(context, it.id) == downloadId } ?: return
        val file = File(ModelDownloads.modelsDir(context), entry.fileName)

        val succeeded = ModelDownloads.statusOf(context, downloadId) == DownloadManager.STATUS_SUCCESSFUL
        val message = when {
            !succeeded -> {
                file.delete()
                "Preuzimanje nije uspjelo: ${entry.name}"
            }
            entry.sha256 != null && !ModelDownloads.sha256Of(file).equals(entry.sha256, ignoreCase = true) -> {
                file.delete()
                "Sažetak se ne poklapa, fajl obrisan: ${entry.name}"
            }
            else -> "Model preuzet: ${entry.name}"
        }
        ModelDownloads.forget(context, entry.id)
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
