package dev.aurora.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import dev.aurora.player.ui.theme.Aurora

import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy

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
    useOpaqueFallback: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = Aurora.shapes.card,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = Aurora.colors

    // Glass is an enhancement, never the only contrast mechanism: a user asking for reduced
    // transparency gets opaque surfaces regardless of what the caller requested.
    val reducedTransparency =
        dev.aurora.player.ui.accessibility.LocalAccessibilityPreferences.current.reducedTransparency

    val backgroundColor: Color = if (useOpaqueFallback || reducedTransparency) {
        colors.surfaceOpaqueFallback
    } else {
        when (level) {
            GlassLevel.Primary -> colors.surfaceGlassPrimary
            GlassLevel.Secondary -> colors.surfaceGlassSecondary
            GlassLevel.Elevated -> colors.surfaceGlassElevated
        }
    }

    val backdrop = dev.aurora.player.ui.theme.LocalAuroraBackdrop.current
    
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (backdrop != null && !useOpaqueFallback && !reducedTransparency) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            blur(64f)
                        },
                        onDrawSurface = {
                            drawRect(color = backgroundColor)
                        }
                    )
                } else {
                    Modifier.background(backgroundColor)
                }
            ),
        content = content,
    )
}
