package dev.aurora.player.ui.artwork

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

/**
 * The one artwork loader for the app, so its cache is shared rather than per-screen.
 */
val LocalArtworkLoader = staticCompositionLocalOf<ArtworkLoader?> { null }

@Composable
fun rememberArtworkLoader(): ArtworkLoader {
    val context = LocalContext.current
    return remember(context) { ArtworkLoader(context.contentResolver) }
}

/**
 * Loads artwork for [artworkUri], or null while it loads and when there is none.
 *
 * Deliberately starts from null on every change of URI rather than holding the previous
 * image: showing the last track's cover under the current track's title is worse than
 * showing the placeholder for a moment.
 */
@Composable
fun rememberArtworkBitmap(artworkUri: String?): State<ImageBitmap?> {
    val loader = LocalArtworkLoader.current ?: rememberArtworkLoader()
    val state = remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(artworkUri, loader) {
        state.value = null
        val bitmap: Bitmap? = loader.bitmapFor(artworkUri)
        state.value = bitmap?.asImageBitmap()
    }

    return state
}

/**
 * The atmosphere colour for [artworkUri].
 *
 * Unlike the bitmap, this keeps the previous colour until the next one is ready. The
 * backdrop fills the screen, so dropping to neutral between tracks would read as a flash
 * rather than as a transition - and the caller animates between values anyway.
 */
@Composable
fun rememberArtworkPalette(artworkUri: String?): State<Color?> {
    val loader = LocalArtworkLoader.current ?: rememberArtworkLoader()
    val state = remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(artworkUri, loader) {
        state.value = loader.paletteFor(artworkUri)
    }

    return state
}
