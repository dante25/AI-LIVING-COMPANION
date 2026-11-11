package io.androllm.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.androllm.core.ui.theme.ledger

/** Jedna traka stanja: natpis, vrijednost 0-100 i boja trake. */
data class AlexaBar(val label: String, val value: Float, val color: Color)

/**
 * Trake stanja Alexine duše (raspoloženje, energija, dosada, ljutnja, privrženost).
 * Prima samo brojeve, pa ga mogu koristiti i početni ekran i postavke.
 */
@Composable
fun AlexaStateBars(
    bars: List<AlexaBar>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
    ) {
        bars.forEach { bar ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.ledger.deskInk),
                    modifier = Modifier.width(if (compact) 84.dp else 96.dp),
                    maxLines = 1
                )
                LinearProgressIndicator(
                    progress = { (bar.value / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(if (compact) 5.dp else 7.dp),
                    color = bar.color,
                    trackColor = MaterialTheme.ledger.deskHairline
                )
                Text(
                    text = bar.value.toInt().toString(),
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.ledger.deskPaper),
                    modifier = Modifier.width(26.dp)
                )
            }
        }
    }
}

/** Standardnih pet traka iz brojeva stanja; boje dolaze iz teme. */
@Composable
fun alexaBars(
    mood: Double,
    energy: Double,
    boredom: Double,
    anger: Double,
    attachment: Double,
): List<AlexaBar> = listOf(
    AlexaBar("Raspoloženje", mood.toFloat(), MaterialTheme.ledger.lampAmber),
    AlexaBar("Energija", energy.toFloat(), MaterialTheme.ledger.revolutNeonEmerald),
    AlexaBar("Dosada", boredom.toFloat(), MaterialTheme.ledger.revolutCyberCyan),
    AlexaBar("Ljutnja", anger.toFloat(), MaterialTheme.ledger.emberRed),
    AlexaBar("Privrženost", attachment.toFloat(), MaterialTheme.ledger.lampDeep),
)
