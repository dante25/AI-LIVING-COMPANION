package io.androllm.core.backup

const val BACKUP_FORMAT_VERSION = 1
const val BACKUP_MANIFEST = "manifest.json"
const val BACKUP_MAX_ENTRY_BYTES = 128L * 1024 * 1024
const val BACKUP_MAX_TOTAL_BYTES = 256L * 1024 * 1024
const val BACKUP_KEEP_SNAPSHOTS = 3

/** Greška s porukom na bosanskom koja se smije prikazati korisniku. */
class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Rezultat uvoza.
 *
 * @property imported ključevi dijelova koji su obnovljeni
 * @property ignored ključevi iz kopije koje ova verzija aplikacije ne poznaje
 * @property failed ključ -> poruka za dijelove čiji uvoz nije uspio (ostali su uvezeni)
 * @property snapshotName naziv sigurnosne kopije postojećih podataka napravljene prije uvoza
 */
data class ImportReport(
    val imported: List<String>,
    val ignored: List<String>,
    val failed: Map<String, String>,
    val snapshotName: String,
)
