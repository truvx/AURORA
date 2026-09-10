package dev.aurora.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.aurora.player.ui.theme.Aurora

/**
 * Provides the environmental backdrop for the AURORA application.
 *
 * In future phases, this will observe the current playback state and
 * render the heavily blurred album artwork.
 *
 * For now, it renders an atmospheric gradient that simulates the lighting
 * of an empty state, setting the visual foundation for the Liquid Glass
 * surfaces floating above it.
 */
@Composable
fun AmbientArtworkLayer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = Aurora.colors

    // Placeholder simulated ambient environment
    val ambientGradient = Brush.radialGradient(
        colors = listOf(
            colors.accentSecondary.copy(alpha = 0.15f), // Inner subtle glow
            colors.backgroundPrimary // Fade out to primary background
        ),
        radius = 1500f
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundPrimary)
            .background(ambientGradient)
    ) {
        content()
    }
}
