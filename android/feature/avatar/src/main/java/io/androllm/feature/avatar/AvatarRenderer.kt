package io.androllm.feature.avatar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Crtač lika. Probni lik je [PlaceholderAvatarRenderer]; pravi (npr. Live2D)
 * dolazi kasnije iza istog sučelja i prima iste parametre.
 */
interface AvatarRenderer {
    @Composable
    fun Render(params: AvatarParams, expression: AvatarExpression, modifier: Modifier)
}
