package dev.aurora.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.aurora.player.ui.accessibility.LocalAccessibilityPreferences
import dev.aurora.player.ui.theme.Aurora
import dev.aurora.player.ui.theme.ContrastScrim

/**
 * The environmental backdrop everything else floats above.
 *
 * Implements the design system's stack: canvas, artwork atmosphere, contrast-protecting
 * scrim, then glass. The scrim is the part that makes translucency safe - measured against
 * the backdrop rather than fixed, because a value strong enough for a white album cover
 * would needlessly crush a dark one.
 *
 * Reduced transparency skips the atmosphere entirely: a user asking for plainer surfaces
 * should not get a tinted backdrop behind them.
 */
@Composable
fun AmbientArtworkLayer(
    modifier: Modifier = Modifier,
    /**
     * Dominant colour of the current artwork, once palette extraction exists. Null renders
     * the neutral atmosphere used when nothing is playing.
     */
    artworkColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = Aurora.colors
    val reducedTransparency = LocalAccessibilityPreferences.current.reducedTransparency

    val backdrop = artworkColor ?: colors.backgroundPrimary

    // Measured against the real composite the user will see, so the guarantee holds for
    // artwork the app has never seen before.
    val scrimAlpha = if (artworkColor == null) {
        0f
    } else {
        ContrastScrim.requiredAlpha(
            foreground = colors.textPrimary,
            backdrop = backdrop,
            glass = colors.surfaceGlassPrimary
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundPrimary)
            .then(
                if (reducedTransparency || artworkColor == null) {
                    // No artwork to protect: keep the existing neutral atmosphere, which is
                    // already measured as legible by ContrastTest.
                    Modifier.background(
                        Brush.radialGradient(
                            colors = listOf(
                                colors.accentSecondary.copy(
                                    alpha = if (reducedTransparency) 0f else 0.15f
                                ),
                                colors.backgroundPrimary
                            ),
                            radius = 1500f
                        )
                    )
                } else {
                    Modifier
                        .background(
                            Brush.radialGradient(
                                colors = listOf(backdrop.copy(alpha = 0.55f), colors.backgroundPrimary),
                                radius = 1500f
                            )
                        )
                        // Sits between artwork and glass, exactly where the design system
                        // places it in the stack.
                        .background(Color.Black.copy(alpha = scrimAlpha))
                }
            )
    ) {
        content()
    }
}
