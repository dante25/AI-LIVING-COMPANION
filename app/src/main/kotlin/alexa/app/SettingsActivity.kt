package alexa.app

import alexa.packs.ContentStore
import alexa.packs.Verdict
import alexa.model.Integrity
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceFragment
import android.widget.Toast
import java.io.File

/** Ekran Postavke: dozvole (prekidači), način rada modela, lokalni modeli, memorija i paketi. */
class SettingsActivity : Activity() {
    private lateinit var packFiles: PackStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packFiles = PackStorage(File(filesDir, "packs"))
        fragmentManager.beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()
    }

    /** Otvara sistemski izbor fajla za paket. */
    fun pickPack() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
        startActivityForResult(intent, REQUEST_PACK)
    }

    @Deprecated("Koristi se za jednostavnost; prelazak na Activity Result API ide uz UI fazu.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PACK && resultCode == RESULT_OK) {
            data?.data?.let { importPack(it) }
        }
    }

    /** Paket se provjerava prije čuvanja. Odbijeni paket se ne čuva. */
    private fun importPack(uri: Uri) {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null) {
            toast("Paket se ne može otvoriti")
            return
        }
        val message = when (val verdict = ContentStore().importZip(bytes)) {
            is Verdict.Accepted -> {
                packFiles.save(Integrity.sha256Hex(bytes) + ".zip", bytes)
                "Paket uvezen: ${verdict.manifest.name}"
            }
            is Verdict.Preserved -> {
                packFiles.save(Integrity.sha256Hex(bytes) + ".zip", bytes)
                "Nepoznat tip, sačuvan kao neaktivan: ${verdict.manifest.name}"
            }
            is Verdict.Rejected -> "Odbijeno: ${verdict.reason}"
        }
        toast(message)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val REQUEST_PACK = 2
    }
}

/**
 * Prekidači i stavke. Vrijednosti se čuvaju u istim postavkama koje čita AppSettings,
 * pa glavni ekran odmah vidi promjenu kad se vrati.
 */
class SettingsFragment : PreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager.sharedPreferencesName = AppSettings.PREFS_NAME
        addPreferencesFromResource(R.xml.settings)

        findPreference("packs").setOnPreferenceClickListener {
            (activity as SettingsActivity).pickPack()
            true
        }
        findPreference("memory").setOnPreferenceClickListener {
            startActivity(Intent(activity, MemoryActivity::class.java))
            true
        }
    }
}
