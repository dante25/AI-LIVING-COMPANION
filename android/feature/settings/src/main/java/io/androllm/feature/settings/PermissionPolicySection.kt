package io.androllm.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import io.androllm.core.tools.api.Tool
import io.androllm.core.tools.api.ToolPermission
import io.androllm.core.tools.audit.AuditDecision
import io.androllm.core.tools.audit.AuditEntry
import io.androllm.core.tools.policy.PermissionPolicy
import io.androllm.core.tools.settings.AutomationSettings
import io.androllm.core.ui.theme.ledger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Bosnian label for a permission policy. */
internal fun PermissionPolicy.bosnianLabel(): String = when (this) {
    PermissionPolicy.OFF -> "Isključeno"
    PermissionPolicy.ASK_EVERY_TIME -> "Pitaj svaki put"
    PermissionPolicy.SESSION -> "Pitaj jednom u sesiji"
}

/** Bosnian name of a logical permission. */
internal fun ToolPermission.bosnianName(): String = when (this) {
    ToolPermission.WEATHER -> "Vremenska prognoza"
    ToolPermission.SEARCH -> "Pretraga weba"
    ToolPermission.SMS -> "SMS poruke"
    ToolPermission.CALLS -> "Pozivi"
    ToolPermission.EMAIL -> "E-pošta"
    ToolPermission.MAPS -> "Mape"
    ToolPermission.CALENDAR -> "Kalendar"
    ToolPermission.ALARMS -> "Alarmi i podsjetnici"
    ToolPermission.CLIPBOARD -> "Međuspremnik"
    ToolPermission.NOTIFICATIONS -> "Obavijesti"
    ToolPermission.FLASHLIGHT -> "Lampa"
    ToolPermission.BLUETOOTH -> "Bluetooth"
    ToolPermission.WIFI -> "Wi-Fi"
    ToolPermission.MUSIC -> "Muzika"
    ToolPermission.APPS -> "Pokretanje aplikacija"
    ToolPermission.CONTACTS -> "Kontakti"
    ToolPermission.CAMERA -> "Kamera"
    ToolPermission.SCREENSHOT -> "Snimak ekrana"
    ToolPermission.SHARE -> "Dijeljenje"
    ToolPermission.SYSTEM -> "Sistemske postavke"
    ToolPermission.ACCESSIBILITY -> "Upravljanje drugim aplikacijama"
    ToolPermission.MCP -> "MCP serveri"
    ToolPermission.NOTES -> "Bilješke"
    ToolPermission.FILES -> "Datoteke i izvoz"
    ToolPermission.VOICE_RECORDER -> "Snimanje glasa"
    ToolPermission.QR -> "QR skener"
    ToolPermission.GITHUB -> "GitHub"
    ToolPermission.CALCULATOR -> "Kalkulator"
    ToolPermission.TRANSLATION -> "Prevođenje"
    ToolPermission.MEDIA -> "Mediji"
    ToolPermission.DEVICE -> "Podaci o uređaju"
    ToolPermission.LOCATION -> "Lokacija"
    ToolPermission.INTERNET -> "Internet"
    ToolPermission.MICROPHONE -> "Mikrofon"
}

private fun AuditDecision.bosnianLabel(): String = when (this) {
    AuditDecision.ALLOWED -> "Dozvoljeno"
    AuditDecision.DENIED -> "Odbijeno"
    AuditDecision.CONFIRMED -> "Potvrđeno"
}

/**
 * Policy chooser per permission (Isključeno / Pitaj svaki put / Pitaj jednom u
 * sesiji) plus the button that opens the audit log ("Dnevnik akcija").
 * Rendered inside [AutomationSection]. Sensitive actions (SMS, pozivi, e-pošta,
 * brisanje) are always confirmed regardless of these choices.
 */
@Composable
fun PermissionPolicyBlock(
    settings: AutomationSettings,
    tools: List<Tool>,
    onUpdate: (AutomationSettings) -> Unit
) {
    var showAuditLog by remember { mutableStateOf(false) }

    val permissions = remember(tools) {
        val used = tools.mapNotNull { it.spec.permission }.toSet() +
            setOf(ToolPermission.INTERNET, ToolPermission.MICROPHONE, ToolPermission.CAMERA)
        ToolPermission.entries.filter { it in used }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.ledger.lampGlow, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Pravila dozvola", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.ledger.deskPaper)
            Text(
                "Osjetljive radnje (poruke, pozivi, e-pošta, brisanje) uvijek traže potvrdu. Preuzimanje modela ne zavisi od ovih pravila.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.ledger.deskInk
            )
        }
    }

    permissions.forEach { permission ->
        PolicyRow(
            permission = permission,
            current = settings.policyFor(permission),
            onSelect = { policy ->
                onUpdate(
                    settings.copy(
                        permissionPolicies = settings.permissionPolicies + (permission.name to policy)
                    )
                )
            }
        )
    }

    HorizontalDivider(color = MaterialTheme.ledger.deskHairline)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showAuditLog = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.ledger.lampGlow, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Dnevnik akcija", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.ledger.deskPaper)
            Text(
                "Pregled svih odluka o dozvolama",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.ledger.deskInk
            )
        }
    }

    if (showAuditLog) {
        AuditLogDialog(onClose = { showAuditLog = false })
    }
}

@Composable
private fun PolicyRow(
    permission: ToolPermission,
    current: PermissionPolicy,
    onSelect: (PermissionPolicy) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = permission.bosnianName(),
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.ledger.deskPaper),
            modifier = Modifier.weight(1f)
        )
        Column {
            TextButton(onClick = { expanded = true }) {
                Text(current.bosnianLabel(), color = MaterialTheme.ledger.lampGlow)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                PermissionPolicy.optionsFor(permission).forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.bosnianLabel()) },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        }
                    )
                }
            }
        }
    }
}

/** Full-screen "Dnevnik akcija" with a confirmed "Obriši dnevnik" action. */
@Composable
fun AuditLogDialog(onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AuditLogScreen(onClose = onClose)
        }
    }
}

@Composable
fun AuditLogScreen(
    onClose: () -> Unit,
    viewModel: AuditLogViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.reload() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Dnevnik akcija",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClose) {
                Text("Zatvori", color = MaterialTheme.ledger.lampGlow)
            }
        }
        TextButton(
            onClick = { confirmClear = true },
            enabled = entries.isNotEmpty(),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text("Obriši dnevnik", color = MaterialTheme.colorScheme.error)
        }
        HorizontalDivider(color = MaterialTheme.ledger.deskHairline)

        if (entries.isEmpty()) {
            Text(
                "Dnevnik je prazan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(entries.asReversed()) { entry ->
                    AuditEntryRow(entry)
                    HorizontalDivider(color = MaterialTheme.ledger.deskHairlineSoft)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Obrisati dnevnik?") },
            text = { Text("Svi zapisi o odlukama bit će trajno obrisani. Ovo se ne može poništiti.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clear()
                }) {
                    Text("Obriši", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Odustani")
                }
            }
        )
    }
}

@Composable
private fun AuditEntryRow(entry: AuditEntry) {
    val time = remember(entry.timestamp) {
        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(entry.timestamp))
    }
    val decisionColor = when (entry.decision) {
        AuditDecision.DENIED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.ledger.lampGlow
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.tool.replace('_', ' '),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.ledger.deskPaper,
                modifier = Modifier.weight(1f)
            )
            Text(
                entry.decision.bosnianLabel(),
                style = MaterialTheme.typography.labelMedium.copy(
                    color = decisionColor,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        val permissionLabel = entry.permission
            ?.let { name -> ToolPermission.entries.firstOrNull { it.name == name }?.bosnianName() }
        Text(
            buildString {
                append(time)
                append(" · sesija #").append(entry.sessionId)
                if (permissionLabel != null) append(" · ").append(permissionLabel)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.ledger.deskInk
        )
        if (entry.detail.isNotBlank()) {
            Text(
                entry.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.ledger.deskInk
            )
        }
    }
}
