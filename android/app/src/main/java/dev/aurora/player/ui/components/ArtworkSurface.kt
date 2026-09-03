package dev.aurora.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.aurora.player.ui.theme.Aurora

/**
 * AURORA artwork display surface.
 *
 * Shows album/playlist/artist artwork with a consistent shape
 * and a meaningful fallback icon when artwork is unavailable.
 * Alt text is always required for accessibility.
 *
 * Phase 1: placeholder fallback only. Future phases add real
 * artwork loading with palette extraction.
 */
@Composable
fun ArtworkSurface(
    altText: String,
    modifier: Modifier = Modifier,
    content: (@Composable BoxScope.() -> Unit)? = null,
) {
    val colors = Aurora.colors
    val shapes = Aurora.shapes

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shapes.artwork)
            .background(colors.surfaceGlassSecondary)
            .semantics { contentDescription = altText },
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) {
            content()
        } else {
            // Fallback: Material Symbol for missing artwork
            Icon(
                imageVector = Icons.Outlined.Album,
                contentDescription = null,
                tint = colors.textTertiary,
            )
        }
    }
}
