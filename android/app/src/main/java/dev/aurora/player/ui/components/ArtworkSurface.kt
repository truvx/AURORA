package dev.aurora.player.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.aurora.player.ui.artwork.rememberArtworkBitmap
import dev.aurora.player.ui.theme.Aurora

/**
 * AURORA artwork display surface.
 *
 * Shows album/playlist/artist artwork with a consistent shape and a meaningful fallback
 * icon when artwork is unavailable. Alt text is always required for accessibility.
 *
 * [artworkUri] is loaded off the main thread and cached by the shared loader. A track with
 * no artwork, or artwork that cannot be read, falls back to the placeholder - artwork is
 * decoration, and failing to load it must never look like a failure to play.
 */
@Composable
fun ArtworkSurface(
    altText: String,
    modifier: Modifier = Modifier,
    artworkUri: String? = null,
    content: (@Composable BoxScope.() -> Unit)? = null,
) {
    val colors = Aurora.colors
    val shapes = Aurora.shapes
    val artwork by rememberArtworkBitmap(artworkUri)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shapes.artwork)
            .background(colors.surfaceGlassSecondary)
            .semantics { contentDescription = altText },
        contentAlignment = Alignment.Center,
    ) {
        val loaded = artwork
        when {
            content != null -> content()

            loaded != null -> Image(
                bitmap = loaded,
                // The surface already carries the description; repeating it here would
                // announce the same thing twice.
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            else -> Icon(
                imageVector = Icons.Outlined.Album,
                contentDescription = null,
                tint = colors.textTertiary,
            )
        }
    }
}
