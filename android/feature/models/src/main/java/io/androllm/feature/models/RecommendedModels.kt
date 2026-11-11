package io.androllm.feature.models

import io.androllm.core.models.DownloadStatus
import io.androllm.core.models.Model
import io.androllm.core.models.ModelFormat
import io.androllm.core.models.ModelStatus
import java.util.Locale

/**
 * A curated, ready-to-download LiteRT-LM model shown in the "Preporuceno za
 * tvoj telefon" section. Pure data: no Android or Compose dependencies.
 *
 * @property repo Hugging Face repo id, e.g. `litert-community/gemma-4-E2B-it-litert-lm`.
 * @property fileName Expected `.litertlm` file name inside [repo]. If the file
 *   does not exist at download time the first `.litertlm` of the repo is used.
 * @property approxSizeMb Approximate download size in MB (decimal, 1 GB = 1000 MB).
 * @property minRamGb Recommended minimum device RAM in GB.
 * @property description Short Bosnian description (who it is for, how demanding).
 */
data class RecommendedModel(
    val id: String,
    val displayName: String,
    val repo: String,
    val fileName: String,
    val approxSizeMb: Int,
    val minRamGb: Float,
    val description: String,
    val family: String = "Other",
    val isTopPick: Boolean = false
)

/** UI-level state of one recommended model card. */
enum class RecommendedStatus { AVAILABLE, DOWNLOADING, PAUSED, DOWNLOADED, FAILED }

object RecommendedModels {

    /**
     * NAPOMENA: repo ids and file names below are NOT VERIFIED (Hugging Face
     * was unreachable when this list was written). PROVJERITI PRI PRVOM
     * PREUZIMANJU. A wrong repo/file produces a readable error message (see
     * ModelsViewModel.downloadRecommended), never a crash; a wrong file name in
     * an existing repo falls back to the first `.litertlm` file of that repo.
     */
    val all: List<RecommendedModel> = listOf(
        RecommendedModel(
            id = "gemma-4-e2b",
            displayName = "Gemma 4 E2B",
            repo = "litert-community/gemma-4-E2B-it-litert-lm",
            fileName = "gemma-4-E2B-it.litertlm",
            approxSizeMb = 2600,
            minRamGb = 6f,
            family = "Gemma",
            description = "Lakši i brži model. Dobar za svakodnevne razgovore i kratke odgovore " +
                "na telefonima sa 6 GB RAM-a ili više."
        ),
        RecommendedModel(
            id = "gemma-4-e4b",
            displayName = "Gemma 4 E4B",
            repo = "litert-community/gemma-4-E4B-it-litert-lm",
            fileName = "gemma-4-E4B-it.litertlm",
            approxSizeMb = 3650,
            minRamGb = 8f,
            family = "Gemma",
            isTopPick = true,
            description = "Najbolji izbor za telefone sa 12 GB RAM-a. Pametniji i detaljniji " +
                "odgovori, ali je zahtjevniji i sporiji od manjeg modela."
        ),
        RecommendedModel(
            id = "qwen-2-5-1-5b",
            displayName = "Qwen 2.5 1.5B Instruct",
            repo = "litert-community/Qwen2.5-1.5B-Instruct",
            fileName = "Qwen2.5-1.5B-Instruct.litertlm",
            approxSizeMb = 1500,
            minRamGb = 4f,
            family = "Qwen",
            description = "Najbrži i najmanji. Koristi ga da provjeriš da sve radi na tvom " +
                "telefonu; odgovori su jednostavniji."
        )
    )

    /** Stable database id of the [Model] created for [rec]. */
    fun modelId(rec: RecommendedModel): String = "recommended-${rec.id}"

    fun downloadUrl(rec: RecommendedModel, fileName: String): String =
        "https://huggingface.co/${rec.repo}/resolve/main/$fileName"

    /**
     * Picks the `.litertlm` file to download from the repo's file list: the
     * expected [RecommendedModel.fileName] if present (case-insensitive),
     * otherwise the first `.litertlm` file (root-level files preferred).
     * Returns null when the repo has no `.litertlm` file at all.
     */
    fun pickLiteRtFile(rec: RecommendedModel, repoFiles: List<String>): String? {
        val litert = repoFiles.filter { it.endsWith(".litertlm", ignoreCase = true) }
        if (litert.isEmpty()) return null
        return litert.firstOrNull { it.equals(rec.fileName, ignoreCase = true) }
            ?: litert.firstOrNull { !it.contains('/') }
            ?: litert.first()
    }

    /**
     * Finds the installed/downloading [Model] that belongs to [rec]: by the
     * stable id, by a Hugging Face URL of the same repo, or by file name.
     * A fully downloaded match wins over a pending one.
     */
    fun matchInstalled(rec: RecommendedModel, installed: List<Model>): Model? {
        val id = modelId(rec)
        val urlPrefix = "https://huggingface.co/${rec.repo}/resolve/"
        val matches = installed.filter { m ->
            m.id == id ||
                m.downloadUrl?.startsWith(urlPrefix, ignoreCase = true) == true ||
                m.filePath?.substringAfterLast('/')?.equals(rec.fileName, ignoreCase = true) == true
        }
        return matches.firstOrNull { it.isDownloaded } ?: matches.firstOrNull()
    }

    /**
     * Combines the persisted [match] with the live WorkManager status [live]
     * (null when unknown) into the card state.
     */
    fun statusOf(match: Model?, live: DownloadStatus?): RecommendedStatus {
        if (match == null) return RecommendedStatus.AVAILABLE
        if (match.isDownloaded) return RecommendedStatus.DOWNLOADED
        val effective = when (live) {
            null, DownloadStatus.NOT_DOWNLOADED -> match.downloadStatus
            else -> live
        }
        return when (effective) {
            DownloadStatus.DOWNLOADED -> RecommendedStatus.DOWNLOADED
            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> RecommendedStatus.DOWNLOADING
            DownloadStatus.PAUSED -> RecommendedStatus.PAUSED
            DownloadStatus.ERROR -> RecommendedStatus.FAILED
            else -> RecommendedStatus.AVAILABLE
        }
    }

    /** True when the device clearly has less RAM than the model needs (0.75 GB tolerance for reserved memory). */
    fun isTooHeavy(rec: RecommendedModel, deviceRamGb: Float?): Boolean =
        deviceRamGb != null && deviceRamGb + 0.75f < rec.minRamGb

    /** "2,6 GB" for sizes of 1000 MB or more, otherwise "800 MB". */
    fun formatSizeMb(mb: Int): String =
        if (mb >= 1000) {
            String.format(Locale.ROOT, "%.1f", mb / 1000.0).replace('.', ',') + " GB"
        } else {
            "$mb MB"
        }

    /** Builds the domain [Model] handed to the existing DownloadManager. */
    fun toModel(rec: RecommendedModel, fileName: String, nowMs: Long = System.currentTimeMillis()): Model =
        Model(
            id = modelId(rec),
            name = rec.displayName,
            description = rec.description,
            filePath = null,
            fileSize = rec.approxSizeMb * 1024L * 1024L,
            format = ModelFormat.LITERTLM,
            downloadUrl = downloadUrl(rec, fileName),
            isDownloaded = false,
            downloadStatus = DownloadStatus.NOT_DOWNLOADED,
            status = ModelStatus.NOT_LOADED,
            createdAt = nowMs,
            updatedAt = nowMs,
            addedDate = nowMs,
            minRamGb = rec.minRamGb,
            recommendedRamGb = rec.minRamGb + 2f,
            family = rec.family
        )
}
