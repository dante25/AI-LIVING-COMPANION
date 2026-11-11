package io.androllm.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import io.androllm.core.ui.theme.ledger
import io.androllm.core.voice.model.VoiceSettings
import io.androllm.core.voice.stt.SttDefaults
import io.androllm.core.voice.stt.WhisperModels

/**
 * Red za izbor jezika prepoznavanja govora (bs, hr, sr, en, automatski) s
 * upozorenjem kad je izabran model koji razumije samo engleski.
 */
@Composable
fun SttLanguageSetting(
    settings: VoiceSettings,
    onUpdate: (VoiceSettings) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDialog = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Translate,
                contentDescription = null,
                tint = MaterialTheme.ledger.lampGlow,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Jezik govora",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.ledger.deskPaper
                )
                Text(
                    text = SttDefaults.languageLabel(settings.sttLanguage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.ledger.deskInk
                )
            }
        }
        if (SttDefaults.needsMultilingualWarning(settings.whisperModel, settings.sttLanguage)) {
            Text(
                text = SttDefaults.ENGLISH_ONLY_WARNING,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.ledger.emberRed,
                modifier = Modifier.padding(start = 48.dp, end = 16.dp, bottom = 8.dp)
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    "Jezik govora",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.ledger.deskPaper
                )
            },
            text = {
                Column {
                    SttDefaults.LANGUAGES.forEach { option ->
                        val selected = settings.sttLanguage.trim().lowercase() == option.code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdate(applySttLanguage(settings, option.code))
                                    showDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.ledger.lampAmber,
                                    unselectedColor = MaterialTheme.ledger.deskInk
                                )
                            )
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.ledger.deskPaper,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Zatvori", color = MaterialTheme.ledger.deskInk)
                }
            }
        )
    }
}

/** Postavlja Whisper jezik; "jezik govora" se prati osim kod "Automatski". */
internal fun applySttLanguage(settings: VoiceSettings, code: String): VoiceSettings =
    if (code == SttDefaults.AUTO) {
        settings.copy(sttLanguage = code)
    } else {
        settings.copy(sttLanguage = code, language = code)
    }

/**
 * Poruka (bez tihe greške) kad izabrani Whisper model nije preuzet, s dugmetom
 * "Preuzmi model". Ne prikazuje se ako je model instaliran.
 *
 * @param installedIds id-jevi preuzetih modela; null = još nepoznato (ništa se ne prikazuje).
 */
@Composable
fun WhisperModelMissingNotice(
    modelId: String,
    installedIds: Set<String>?,
    download: Pair<String, Float>?,
    onDownload: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (installedIds == null || modelId in installedIds) return
    val model = WhisperModels.byId(modelId) ?: return
    val downloading = download?.first == modelId

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(MaterialTheme.ledger.emberRed.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "Model za prepoznavanje govora (${model.displayName}, ${model.sizeLabel}) nije preuzet. " +
                "Glasovni unos neće raditi dok ga ne preuzmeš.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.ledger.deskPaper
        )
        if (downloading) {
            LinearProgressIndicator(
                progress = { download?.second ?: 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(6.dp),
                color = MaterialTheme.ledger.lampGlow,
                trackColor = MaterialTheme.ledger.deskHairline
            )
        }
        TextButton(
            enabled = !downloading,
            onClick = { onDownload(modelId) }
        ) {
            Text(
                text = if (downloading) "Preuzimanje…" else "Preuzmi model",
                color = MaterialTheme.ledger.lampAmber
            )
        }
    }
}
