package dev.aurora.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.aurora.player.ui.theme.Aurora

/**
 * AURORA glass card for grouped content.
 *
 * A card-shaped glass surface with standard padding.
 * Supports loading, empty, disabled, and focused states
 * through modifier and content composition.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.Primary,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = Aurora.colors
    val shapes = Aurora.shapes
    val spacing = Aurora.spacing

    GlassSurface(
        modifier = modifier,
        level = level,
    ) {
        Box(
            modifier = Modifier.padding(spacing.space4),
            content = content,
        )
    }
}
