package alexa.app

import alexa.permissions.Capability
import alexa.permissions.Settings as PermissionSettings
import android.content.Context
import java.io.File
import java.io.InputStream

/** Dozvole u postavkama se pamte između pokretanja. Dozvole u sesiji se ne pamte. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): PermissionSettings {
        val settings = PermissionSettings()
        Capability.entries.forEach { settings.set(it, isEnabled(it)) }
        return settings
    }

    fun isEnabled(capability: Capability): Boolean = prefs.getBoolean(capability.name, false)

    companion object {
        /** Isti naziv koristi i ekran Postavke, pa su vrijednosti jedne te iste. */
        const val PREFS_NAME = "alexa-settings"
    }

    fun save(capability: Capability, enabled: Boolean) {
        prefs.edit().putBoolean(capability.name, enabled).apply()
    }

    /** "LOCAL" ili "CLOUD". Oblak se pamti, ali dok nema API ključa radi se lokalno. */
    fun modelMode(): String = prefs.getString("model_mode", "LOCAL") ?: "LOCAL"

    fun setModelMode(mode: String) {
        prefs.edit().putString("model_mode", mode).apply()
    }
}

/** Lokalni modeli: fajlovi se kopiraju u aplikaciju i brišu se na zahtjev. Memorija se ne dira. */
class ModelStorage(private val dir: File) {
    fun list(): List<File> = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()

    fun importStream(name: String, input: InputStream) {
        dir.mkdirs()
        File(dir, safeName(name)).outputStream().use { input.copyTo(it) }
    }

    fun delete(file: File) {
        file.delete()
    }

    private fun safeName(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
}

/** Uvezeni paketi se čuvaju kao zip fajlovi, pa se ponovo učitavaju pri pokretanju. */
class PackStorage(private val dir: File) {
    fun save(name: String, bytes: ByteArray) {
        dir.mkdirs()
        File(dir, name).writeBytes(bytes)
    }

    fun loadAll(): List<ByteArray> =
        dir.listFiles { f -> f.extension == "zip" }
            ?.sortedBy { it.name }
            ?.map { it.readBytes() }
            ?: emptyList()
}
