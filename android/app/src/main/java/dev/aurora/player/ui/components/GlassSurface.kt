package dev.aurora.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import dev.aurora.player.ui.theme.Aurora

/**
 * Base AURORA glass material surface.
 *
 * Provides translucent background with the AURORA glass recipe.
 * Falls back to opaque surface when reduced transparency is active
 * or when the platform cannot composite efficiently.
 *
 * Phase 1: Uses solid-with-alpha tint. Future phases add backdrop
 * blur when the artwork atmosphere system is implemented.
 *
 * @param level Glass depth level controlling opacity and tint
 * @param useOpaqueFallback Force opaque rendering for accessibility/performance
 */
enum class GlassLevel { Primary, Secondary, Elevated }

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.Primary,
    useOpaqueFallback: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = Aurora.colors
    val shapes = Aurora.shapes

    val backgroundColor: Color = if (useOpaqueFallback) {
        colors.surfaceOpaqueFallback
    } else {
        when (level) {
            GlassLevel.Primary -> colors.surfaceGlassPrimary
            GlassLevel.Secondary -> colors.surfaceGlassSecondary
            GlassLevel.Elevated -> colors.surfaceGlassElevated
        }
    }

    Box(
        modifier = modifier
            .clip(shapes.card)
            .background(backgroundColor),
        content = content,
    )
}
