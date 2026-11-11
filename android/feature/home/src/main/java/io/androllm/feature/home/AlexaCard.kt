package io.androllm.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.androllm.core.soul.SoulState
import io.androllm.core.soul.describe
import io.androllm.core.ui.components.AlexaStateBars
import io.androllm.core.ui.components.CloudGlassCard
import io.androllm.core.ui.components.alexaBars
import io.androllm.core.ui.theme.ledger

/** Kartica "Alexa" na početnom ekranu: raspoloženje riječima i male trake stanja. */
@Composable
internal fun AlexaCard(soul: SoulState, modifier: Modifier = Modifier) {
    CloudGlassCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Alexa",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.ledger.deskPaper
                )
            )
            Text(
                text = soul.describe(),
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.ledger.lampDeep)
            )
            AlexaStateBars(
                bars = alexaBars(
                    mood = soul.mood,
                    energy = soul.energy,
                    boredom = soul.boredom,
                    anger = soul.anger,
                    attachment = soul.attachment
                ),
                compact = true
            )
        }
    }
}
