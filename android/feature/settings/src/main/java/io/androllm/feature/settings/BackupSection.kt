package io.androllm.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.androllm.core.ui.components.CloudCapsuleButton
import io.androllm.core.ui.components.CloudGlassCard
import io.androllm.core.ui.theme.ledger

/**
 * Sigurnosna kopija: "Izvezi sve" pravi jedan zip, "Uvezi sve" ga obnavlja.
 * Fajl se bira sistemskim izborom fajla (CreateDocument / OpenDocument).
 */
@Composable
fun BackupSection(
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmImport by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> viewModel.export(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.import(uri) }

    CloudGlassCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Sve na jednom mjestu",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.ledger.deskPaper
                )
            )
            Text(
                text = "Jedan zip sa memorijom, razgovorima, opštim postavkama i stanjem duše. " +
                    "Ključevi, tokeni i modeli se ne uključuju.",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.ledger.deskInk)
            )
            CloudCapsuleButton(
                text = "Izvezi sve",
                onClick = { exportLauncher.launch(viewModel.suggestedFileName()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
                icon = Icons.Filled.IosShare
            )
            CloudCapsuleButton(
                text = "Uvezi sve",
                onClick = { confirmImport = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
                icon = Icons.Filled.FileUpload
            )
            if (state.busy) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.ledger.lampAmber
                    )
                    Text(
                        text = "Radim, sačekaj...",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.ledger.deskInk)
                    )
                }
            }
            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (state.isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.ledger.deskPaper
                    )
                )
                TextButton(onClick = { viewModel.clearMessage() }) {
                    Text("U redu")
                }
            }
        }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Uvezi sve?") },
            text = {
                Text(
                    "Podaci iz kopije se spajaju s postojećim, a ponovljen uvoz ne umnožava poruke. " +
                        "Prije uvoza se automatski pravi sigurnosna kopija trenutnih podataka " +
                        "(čuvaju se zadnje tri)."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    importLauncher.launch(
                        arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
                    )
                }) { Text("Izaberi fajl") }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = false }) { Text("Odustani") }
            }
        )
    }
}
