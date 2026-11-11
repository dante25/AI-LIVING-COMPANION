package io.androllm.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.androllm.core.backup.BackupException
import io.androllm.core.backup.BackupManager
import io.androllm.core.backup.ImportReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Stanje ekrana "Sigurnosna kopija". */
data class BackupUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

/**
 * "Izvezi sve" i "Uvezi sve": jedan zip sa memorijom, razgovorima, postavkama (bez ključeva)
 * i stanjem duše. Modeli se ne uključuju.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupManager: BackupManager,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "androllm-sigurnosna-kopija-$stamp.zip"
    }

    fun export(uri: Uri?) {
        if (uri == null || _state.value.busy) return
        viewModelScope.launch {
            _state.value = BackupUiState(busy = true)
            try {
                val stream = withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt") }
                    ?: throw BackupException("Fajl se ne može otvoriti za pisanje")
                backupManager.exportAll(stream)
                _state.value = BackupUiState(message = "Izvoz je završen. Kopija ne sadrži ključeve ni modele.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = BackupUiState(message = "Izvoz nije uspio: ${e.message}", isError = true)
            }
        }
    }

    fun import(uri: Uri?) {
        if (uri == null || _state.value.busy) return
        viewModelScope.launch {
            _state.value = BackupUiState(busy = true)
            try {
                val stream = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri) }
                    ?: throw BackupException("Fajl se ne može otvoriti za čitanje")
                val report = backupManager.importAll(stream)
                _state.value = BackupUiState(message = describe(report), isError = report.failed.isNotEmpty())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = BackupUiState(message = "Uvoz nije uspio: ${e.message}", isError = true)
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null, isError = false)
    }

    private fun label(key: String): String = when (key) {
        "memory" -> "memorija"
        "conversations" -> "razgovori"
        "settings" -> "postavke"
        "soul" -> "duša"
        else -> key
    }

    private fun describe(report: ImportReport): String {
        val parts = mutableListOf<String>()
        if (report.imported.isNotEmpty()) {
            parts += "Obnovljeno: ${report.imported.joinToString(", ") { label(it) }}."
        }
        if (report.failed.isNotEmpty()) {
            parts += "Nije uspjelo: ${report.failed.entries.joinToString("; ") { "${label(it.key)} (${it.value})" }}."
        }
        if (report.ignored.isNotEmpty()) {
            parts += "Preskočeno (nepoznato): ${report.ignored.joinToString(", ")}."
        }
        parts += "Prethodni podaci su sačuvani u ${report.snapshotName}."
        return parts.joinToString(" ")
    }
}
