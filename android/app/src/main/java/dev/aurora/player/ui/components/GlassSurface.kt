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
 * Translucent background with the AURORA glass recipe: the surface samples what is behind
 * it, blurs and saturates it, then lays its own tint over the top. Material weight carries
 * hierarchy, so the blur radius follows the level rather than being one shared value.
 *
 * Falls back to opaque when the user has asked for reduced transparency, or when a caller
 * asks for it explicitly. Glass is never the only thing providing contrast: legibility over
 * the composited surface is asserted in GlassContrastTest, and artwork bright enough to
 * threaten it is handled by the scrim in AmbientArtworkLayer.
 *
 * @param level Glass depth level controlling tint opacity and blur radius
 * @param useOpaqueFallback Force opaque rendering for a surface that cannot afford glass
 */
enum class GlassLevel { Primary, Secondary, Elevated }

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.Primary,
    useOpaqueFallback: Boolean = false,
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
    val depth = Aurora.depth

    // Material weight carries hierarchy: a floating surface has to read as a thicker piece
    // of glass than a small chip, and blur is what says so. One shared radius across every
    // level - which is what this was - flattens all three into the same material.
    val blurRadius = with(androidx.compose.ui.platform.LocalDensity.current) {
        when (level) {
            GlassLevel.Primary -> depth.blurSurface
            GlassLevel.Secondary -> depth.blurSubtle
            GlassLevel.Elevated -> depth.blurFloating
        }.toPx()
    }

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
                            blur(blurRadius)
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
