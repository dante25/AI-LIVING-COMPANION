package io.androllm.feature.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.androllm.core.models.DownloadProgress
import io.androllm.core.models.Model
import io.androllm.core.ui.theme.ledger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Adds the "Preporuceno za tvoj telefon" header and one card per
 * [RecommendedModels.all] entry to a LazyColumn.
 */
fun LazyListScope.recommendedModelsItems(
    installedModels: List<Model>,
    resolvingIds: Set<String>,
    errors: Map<String, String>,
    deviceRamGb: Float?,
    observeProgress: (String) -> Flow<DownloadProgress?>,
    onDownload: (RecommendedModel) -> Unit,
    onPause: (String) -> Unit,
    onResume: (Model) -> Unit,
    onCancel: (String) -> Unit
) {
    item(key = "recommended_header") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Preporučeno za tvoj telefon",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.ledger.deskPaper
            )
            Text(
                text = "Odaberi jedan model i preuzmi ga. Ne moraš ništa tražiti.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.ledger.deskInk
            )
        }
    }
    items(RecommendedModels.all.size, key = { "recommended_${RecommendedModels.all[it].id}" }) { index ->
        val rec = RecommendedModels.all[index]
        RecommendedModelCard(
            rec = rec,
            match = RecommendedModels.matchInstalled(rec, installedModels),
            resolving = rec.id in resolvingIds,
            error = errors[rec.id],
            deviceRamGb = deviceRamGb,
            observeProgress = observeProgress,
            onDownload = { onDownload(rec) },
            onPause = onPause,
            onResume = onResume,
            onCancel = onCancel
        )
    }
}

/** Shown on the Installed tab when the user has no downloaded model yet. */
@Composable
fun RecommendedEmptyState(
    installedModels: List<Model>,
    resolvingIds: Set<String>,
    errors: Map<String, String>,
    deviceRamGb: Float?,
    listState: LazyListState,
    observeProgress: (String) -> Flow<DownloadProgress?>,
    onDownload: (RecommendedModel) -> Unit,
    onPause: (String) -> Unit,
    onResume: (Model) -> Unit,
    onCancel: (String) -> Unit,
    onImportClick: () -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        recommendedModelsItems(
            installedModels = installedModels,
            resolvingIds = resolvingIds,
            errors = errors,
            deviceRamGb = deviceRamGb,
            observeProgress = observeProgress,
            onDownload = onDownload,
            onPause = onPause,
            onResume = onResume,
            onCancel = onCancel
        )
        item(key = "recommended_import") {
            OutlinedButton(onClick = onImportClick, modifier = Modifier.fillMaxWidth()) {
                Text("Ili uvezi vlastiti .litertlm fajl")
            }
        }
    }
}

@Composable
private fun RecommendedModelCard(
    rec: RecommendedModel,
    match: Model?,
    resolving: Boolean,
    error: String?,
    deviceRamGb: Float?,
    observeProgress: (String) -> Flow<DownloadProgress?>,
    onDownload: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (Model) -> Unit,
    onCancel: (String) -> Unit
) {
    val pendingId = match?.takeIf { !it.isDownloaded }?.id
    val progress by remember(pendingId) {
        if (pendingId != null) observeProgress(pendingId) else flowOf<DownloadProgress?>(null)
    }.collectAsStateWithLifecycle(initialValue = null)

    val status = RecommendedModels.statusOf(match, progress?.status)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.ledger.deskWalnut)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = rec.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.ledger.deskPaper,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (rec.isTopPick) {
                    Surface(
                        color = MaterialTheme.ledger.lampAmber,
                        contentColor = MaterialTheme.ledger.inkOnLamp,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Preporučeno",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Text(
                text = "${RecommendedModels.formatSizeMb(rec.approxSizeMb)} • preporučeno ${rec.minRamGb.toInt()} GB RAM-a",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.ledger.deskInk
            )
            Text(
                text = rec.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.ledger.deskPaper
            )

            if (RecommendedModels.isTooHeavy(rec, deviceRamGb)) {
                Text(
                    text = "Tvoj telefon ima oko ${"%.1f".format(deviceRamGb ?: 0f)} GB RAM-a. " +
                        "Ovaj model bi mogao biti prespor ili se neće učitati.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            when (status) {
                RecommendedStatus.DOWNLOADED -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.ledger.lampDeep,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Preuzeto",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.ledger.lampDeep
                    )
                }

                RecommendedStatus.DOWNLOADING, RecommendedStatus.PAUSED -> {
                    val percent = (progress?.progressPercent ?: 0).coerceIn(0, 100)
                    val indeterminate = status == RecommendedStatus.DOWNLOADING &&
                        (progress?.totalBytes ?: 0L) <= 0L && percent == 0
                    if (indeterminate) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp))
                    } else {
                        LinearProgressIndicator(
                            progress = percent / 100f,
                            modifier = Modifier.fillMaxWidth().height(8.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (status == RecommendedStatus.PAUSED) "Pauzirano ($percent%)" else "Preuzimanje… $percent%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.ledger.deskInk,
                            modifier = Modifier.weight(1f)
                        )
                        if (match != null) {
                            if (status == RecommendedStatus.PAUSED) {
                                TextButton(onClick = { onResume(match) }) { Text("Nastavi") }
                            } else {
                                TextButton(onClick = { onPause(match.id) }) { Text("Pauziraj") }
                            }
                            TextButton(onClick = { onCancel(match.id) }) {
                                Text("Otkaži", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                RecommendedStatus.AVAILABLE, RecommendedStatus.FAILED -> {
                    if (status == RecommendedStatus.FAILED && error == null) {
                        Text(
                            text = progress?.errorMessage
                                ?: "Preuzimanje nije uspjelo. Provjeri internet vezu i pokušaj ponovo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        onClick = onDownload,
                        enabled = !resolving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            when {
                                resolving -> "Tražim fajl…"
                                status == RecommendedStatus.FAILED -> "Pokušaj ponovo"
                                else -> "Preuzmi"
                            }
                        )
                    }
                }
            }
        }
    }
}
