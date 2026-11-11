package io.androllm.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import io.androllm.core.soul.AvatarSize
import io.androllm.core.soul.describe
import io.androllm.core.ui.components.AlexaStateBars
import io.androllm.core.ui.components.CloudGlassCard
import io.androllm.core.ui.components.alexaBars
import io.androllm.core.ui.theme.ledger

/** Jednolinijska registracija u [SettingsScreen]: akordeon "Alexa". */
internal fun LazyListScope.alexaAccordionItem(
    expanded: Boolean,
    onToggle: () -> Unit,
    searchQuery: String,
    reduceMotion: Boolean
) {
    settingsAccordionItem(
        group = SettingsGroup.Alexa,
        icon = Icons.Filled.Favorite,
        expanded = expanded,
        onToggle = onToggle,
        visible = SettingsGroup.Alexa.matches(searchQuery),
        subtitle = "Karakter i duša",
        reduceMotion = reduceMotion
    ) {
        AlexaSection()
    }
}

/**
 * "Alexa": uređivanje karaktera, prekidač duše, prikaz trenutnog stanja duše
 * i resetovanje duše.
 */
@Composable
fun AlexaSection(viewModel: AlexaViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val soul by viewModel.soulState.collectAsStateWithLifecycle()

    var draft by remember(settings.persona) { mutableStateOf(settings.persona) }
    var confirmReset by remember { mutableStateOf(false) }
    val dirty = draft.trim() != settings.persona.trim()

    CloudGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Karakter",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.ledger.deskPaper
                )
            )
            Text(
                text = "Tekst koji Alexi govori ko je i kako da se ponaša. Ide kao prva poruka u svakom razgovoru.",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.ledger.deskInk)
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp),
                minLines = 5,
                maxLines = 12,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.ledger.lampAmber,
                    unfocusedBorderColor = MaterialTheme.ledger.deskHairline,
                    focusedTextColor = MaterialTheme.ledger.deskPaper,
                    unfocusedTextColor = MaterialTheme.ledger.deskPaper,
                    cursorColor = MaterialTheme.ledger.lampAmber
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.savePersona(draft) },
                    enabled = dirty,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.ledger.lampAmber,
                        contentColor = MaterialTheme.ledger.inkOnLamp
                    )
                ) {
                    Text("Sačuvaj")
                }
                TextButton(onClick = { viewModel.restoreDefaultPersona() }) {
                    Text("Vrati podrazumijevano", color = MaterialTheme.ledger.lampDeep)
                }
            }

            HorizontalDivider(color = MaterialTheme.ledger.deskHairline)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Duša uključena",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.ledger.deskPaper
                        )
                    )
                    Text(
                        text = "Kad je isključena, Alexa nema raspoloženja: ne dobija uputu o stanju i događaji se ne primjenjuju.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.ledger.deskInk)
                    )
                }
                Switch(
                    checked = settings.soulEnabled,
                    onCheckedChange = { viewModel.setSoulEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.ledger.lampGlow,
                        checkedTrackColor = MaterialTheme.ledger.lampDeep,
                        checkedBorderColor = MaterialTheme.ledger.lampDeep,
                        uncheckedThumbColor = MaterialTheme.ledger.deskInk,
                        uncheckedTrackColor = MaterialTheme.ledger.deskHairline,
                        uncheckedBorderColor = MaterialTheme.ledger.deskHairline
                    )
                )
            }

            HorizontalDivider(color = MaterialTheme.ledger.deskHairline)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Prikaži lik Alexe",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.ledger.deskPaper
                        )
                    )
                    Text(
                        text = "Lik lebdi preko ekrana razgovora i početnog ekrana. Možeš joj reći \"Alexa, miči se\".",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.ledger.deskInk)
                    )
                }
                Switch(
                    checked = settings.showAvatar,
                    onCheckedChange = { viewModel.setShowAvatar(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.ledger.lampGlow,
                        checkedTrackColor = MaterialTheme.ledger.lampDeep,
                        checkedBorderColor = MaterialTheme.ledger.lampDeep,
                        uncheckedThumbColor = MaterialTheme.ledger.deskInk,
                        uncheckedTrackColor = MaterialTheme.ledger.deskHairline,
                        uncheckedBorderColor = MaterialTheme.ledger.deskHairline
                    )
                )
            }

            if (settings.showAvatar) {
                Text(
                    text = "Veličina lika",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.ledger.deskPaper
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AvatarSize.entries.forEach { size ->
                        RadioButton(
                            selected = settings.avatarSize == size,
                            onClick = { viewModel.setAvatarSize(size) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.ledger.lampDeep,
                                unselectedColor = MaterialTheme.ledger.deskInk
                            )
                        )
                        Text(
                            text = size.label,
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.ledger.deskPaper),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.ledger.deskHairline)

            Text(
                text = "Trenutno stanje duše",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.ledger.deskPaper
                )
            )
            Text(
                text = soul.describe(),
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.ledger.lampDeep)
            )
            AlexaStateBars(
                bars = alexaBars(
                    mood = soul.mood,
                    energy = soul.energy,
                    boredom = soul.boredom,
                    anger = soul.anger,
                    attachment = soul.attachment
                )
            )
            TextButton(
                onClick = { confirmReset = true },
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text("Resetuj dušu", color = MaterialTheme.ledger.emberRed)
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Resetovati dušu?") },
            text = {
                Text("Raspoloženje, energija, dosada, ljutnja i privrženost vraćaju se na početne vrijednosti.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetSoul()
                    confirmReset = false
                }) {
                    Text("Resetuj", color = MaterialTheme.ledger.emberRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text("Odustani")
                }
            }
        )
    }
}
