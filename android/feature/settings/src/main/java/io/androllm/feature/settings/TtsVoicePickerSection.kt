package io.androllm.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.androllm.core.ui.theme.ledger
import io.androllm.core.voice.model.VoiceSettings
import io.androllm.core.voice.tts.TtsEngineIds
import io.androllm.core.voice.tts.TtsVoiceInfo
import java.util.Locale

/**
 * Izbor motora za govor i glasa Alexe (sistemski glasovi telefona),
 * sa preslušavanjem. Uključuje se jednom linijom u [VoiceAssistantSection].
 */
@Composable
fun TtsVoicePickerSection(
    settings: VoiceSettings,
    onUpdate: (VoiceSettings) -> Unit,
    viewModel: TtsVoicePickerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val engineId = TtsEngineIds.normalize(settings.ttsEngine)

    LaunchedEffect(engineId) {
        if (engineId == TtsEngineIds.SYSTEM) viewModel.loadVoices()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.ledger.deskHairline)

        Text(
            text = "Glas Alexe",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.ledger.deskPaper
            ),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
        )
        Text(
            text = "Izaberi motor govora. Sistemski glasovi telefona imaju hrvatski i srpski, " +
                "što je najbliže bosanskom.",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.ledger.deskInk),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        EngineRow(
            title = "Sistemski glas",
            subtitle = "Glasovi instalirani na telefonu (preporučeno)",
            selected = engineId == TtsEngineIds.SYSTEM,
            onSelect = { onUpdate(settings.copy(ttsEngine = TtsEngineIds.SYSTEM)) }
        )
        EngineRow(
            title = "Piper (engleski)",
            subtitle = "Offline engleski glas, loš za bosanski",
            selected = engineId == TtsEngineIds.PIPER,
            onSelect = { onUpdate(settings.copy(ttsEngine = TtsEngineIds.PIPER)) }
        )
        EngineRow(
            title = "Gemini (cloud)",
            subtitle = "Zahtijeva internet i Gemini ključ",
            selected = engineId == TtsEngineIds.GEMINI,
            onSelect = { onUpdate(settings.copy(ttsEngine = TtsEngineIds.GEMINI)) }
        )

        if (engineId == TtsEngineIds.SYSTEM) {
            HorizontalDivider(color = MaterialTheme.ledger.deskHairline)

            Text(
                text = "Android ne javlja da li je glas ženski ili muški, zato izaberi preslušavanjem.",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.ledger.deskInk),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            VoiceRow(
                title = "Automatski",
                subtitle = "Najbolji dostupni glas: bosanski, hrvatski, srpski, pa engleski",
                selected = settings.systemVoiceName.isBlank(),
                playing = ui.previewing == "",
                onSelect = { onUpdate(settings.copy(systemVoiceName = "")) },
                onPreview = { viewModel.preview(engineId, "", settings.speakingSpeed) },
                onStop = viewModel::stopPreview
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                ui.voices.forEach { voice ->
                    VoiceRow(
                        title = voice.name,
                        subtitle = voiceSubtitle(voice),
                        subtitleIsWarning = voice.networkRequired,
                        selected = settings.systemVoiceName == voice.name,
                        playing = ui.previewing == voice.name,
                        onSelect = { onUpdate(settings.copy(systemVoiceName = voice.name)) },
                        onPreview = { viewModel.preview(engineId, voice.name, settings.speakingSpeed) },
                        onStop = viewModel::stopPreview
                    )
                }
            }

            if (ui.loading) {
                Text(
                    text = "Učitavam glasove…",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.ledger.deskInk),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { openInstallVoices(context) }) {
                    Text("Instaliraj glasove")
                }
                TextButton(onClick = { viewModel.loadVoices() }) {
                    Text("Osvježi")
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                PreviewButton(
                    playing = ui.previewing != null,
                    onPreview = { viewModel.preview(engineId, "", settings.speakingSpeed) },
                    onStop = viewModel::stopPreview
                )
            }
        }

        val message = ui.message
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.ledger.emberRed),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        Spacer(modifier = Modifier.padding(bottom = 6.dp))
    }
}

@Composable
private fun EngineRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.ledger.lampGlow,
                unselectedColor = MaterialTheme.ledger.deskInk
            )
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.ledger.deskPaper)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.ledger.deskInk)
            )
        }
    }
}

@Composable
private fun VoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    playing: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
    onStop: () -> Unit,
    subtitleIsWarning: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.ledger.lampGlow,
                unselectedColor = MaterialTheme.ledger.deskInk
            )
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.ledger.deskPaper)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (subtitleIsWarning) MaterialTheme.ledger.emberRed else MaterialTheme.ledger.deskInk
                )
            )
        }
        PreviewButton(playing = playing, onPreview = onPreview, onStop = onStop)
    }
}

@Composable
private fun PreviewButton(
    playing: Boolean,
    onPreview: () -> Unit,
    onStop: () -> Unit
) {
    Button(
        onClick = if (playing) onStop else onPreview,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.ledger.lampAmber,
            contentColor = MaterialTheme.ledger.inkOnLamp
        )
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.padding(end = 4.dp)
        )
        Text(if (playing) "Zaustavi" else "Poslušaj")
    }
}

private fun voiceSubtitle(voice: TtsVoiceInfo): String {
    val language = Locale.forLanguageTag(voice.languageTag).displayName
        .ifBlank { voice.languageTag }
    val network = if (voice.networkRequired) "treba internet" else "radi bez interneta"
    return "$language · $network"
}

/** Otvara instalaciju glasova; ako je nema, sistemske postavke TTS-a, pa opšte postavke. */
private fun openInstallVoices(context: Context) {
    val attempts = listOf(
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
        Intent("com.android.settings.TTS_SETTINGS"),
        Intent(Settings.ACTION_SETTINGS)
    )
    for (intent in attempts) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            // probaj sljedeću opciju
        } catch (e: SecurityException) {
            // probaj sljedeću opciju
        }
    }
}
