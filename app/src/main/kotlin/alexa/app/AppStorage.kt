package alexa.app

import alexa.permissions.Capability
import alexa.permissions.Settings as PermissionSettings
import android.content.Context
import java.io.File

/** Dozvole u postavkama se pamte između pokretanja. Dozvole u sesiji se ne pamte. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("alexa-settings", Context.MODE_PRIVATE)

    fun load(): PermissionSettings {
        val settings = PermissionSettings()
        Capability.entries.forEach { settings.set(it, prefs.getBoolean(it.name, false)) }
        return settings
    }

    fun save(capability: Capability, enabled: Boolean) {
        prefs.edit().putBoolean(capability.name, enabled).apply()
    }
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
