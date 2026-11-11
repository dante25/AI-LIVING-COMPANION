package io.androllm.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.androllm.core.ui.theme.ledger
import io.androllm.core.voice.model.VoiceSettings

/**
 * Prekidač "Čitaj odgovore naglas": Alexa čita svaki odgovor u običnom chatu,
 * bez potrebe da je glasovni asistent uključen.
 */
@Composable
fun ReadRepliesAloudRow(
    settings: VoiceSettings,
    onUpdate: (VoiceSettings) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onUpdate(settings.copy(readRepliesAloud = !settings.readRepliesAloud)) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.VolumeUp,
            contentDescription = null,
            tint = MaterialTheme.ledger.lampDeep,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Čitaj odgovore naglas",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.ledger.deskPaper
                )
            )
            Text(
                text = "Alexa naglas čita svaki odgovor u razgovoru",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.ledger.deskInk)
            )
        }
        Switch(
            checked = settings.readRepliesAloud,
            onCheckedChange = { onUpdate(settings.copy(readRepliesAloud = it)) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.ledger.lampGlow,
                checkedTrackColor = MaterialTheme.ledger.lampDeep,
                checkedBorderColor = MaterialTheme.ledger.deskHairline,
                uncheckedThumbColor = MaterialTheme.ledger.lampGlow,
                uncheckedTrackColor = MaterialTheme.ledger.deskWalnutRaised,
                uncheckedBorderColor = MaterialTheme.ledger.deskHairline
            )
        )
    }
}
