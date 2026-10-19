package dev.aurora.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import dev.aurora.player.AuroraApp
import dev.aurora.player.ui.artwork.LocalArtworkLoader
import dev.aurora.player.ui.artwork.rememberArtworkLoader
import dev.aurora.player.ui.components.LocalPlayerCoordinator
import dev.aurora.player.ui.navigation.AuroraNavigation
import dev.aurora.player.ui.theme.AuroraTheme
import dev.aurora.player.ui.theme.DeviceCapabilities

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.aurora.player.ui.theme.LocalAuroraBackdrop
import dev.aurora.player.ui.theme.LocalDeviceCapabilities

/**
 * Root composable for the AURORA application.
 *
 * Wraps the navigation graph in the AURORA theme.
 * Player coordinator will be provided here in Phase 5+.
 */
@Composable
fun AuroraAppRoot() {
    val context = LocalContext.current
    val container = (context.applicationContext as AuroraApp).container

    // Resolved once at the root so every surface reacts to the same accessibility state.
    val accessibility = dev.aurora.player.ui.accessibility.rememberAccessibilityPreferences()

    // One loader for the app, so its cache is shared between the lists, the mini-player and
    // the full player rather than each decoding the same cover.
    val artworkLoader = rememberArtworkLoader()

    CompositionLocalProvider(
        LocalPlayerCoordinator provides container.playerCoordinator,
        LocalArtworkLoader provides artworkLoader,
        dev.aurora.player.ui.artwork.LocalCoverArtRepository provides container.coverArtRepository,
        dev.aurora.player.ui.lyrics.LocalLyricsRepository provides container.lyricsRepository,
        dev.aurora.player.ui.accessibility.LocalAccessibilityPreferences provides accessibility,
        LocalDeviceCapabilities provides DeviceCapabilities.from(context)
    ) {
        AuroraTheme {
            /*
             * In the floating window, the app is the player and nothing else.
             *
             * At that size the chrome, the feed and the mini-player are not smaller
             * versions of themselves - they are clutter around a video the listener is
             * watching while doing something else. Returning early also keeps the whole
             * navigation graph out of composition while the window is up.
             */
            if (LocalPictureInPicture.current.active) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black)
                )
                return@AuroraTheme
            }

            val navController = rememberNavController()
            // Two captured layers, for two different jobs.
            //
            // `backdrop` is the artwork atmosphere alone, and it is what glass *inside* the
            // page samples. `contentBackdrop` captures the page itself, so the floating
            // chrome can refract the list scrolling beneath it - which is what the
            // references show and what a bar sampling only a flat wash cannot do.
            //
            // The chrome is drawn outside both layers. Glass that samples the layer it is
            // drawn into puts the render node inside itself, and the render thread
            // overflows its stack: a native crash on every launch, which is exactly what
            // happened when the backdrop wrapped the whole navigation.
            val backdrop = rememberLayerBackdrop()
            val contentBackdrop = rememberLayerBackdrop()

            CompositionLocalProvider(
                LocalAuroraBackdrop provides backdrop
            ) {
                /*
                 * The backdrop layer holds the artwork atmosphere and nothing else.
                 *
                 * It used to wrap the whole navigation, which meant the glass surfaces
                 * inside it sampled the very layer they were drawn into. The render node
                 * then contained itself, and prepareTree recursed until the render thread
                 * blew its stack: a native SIGSEGV at 512 frames, every launch. That is
                 * what the opaque-by-default fallback was really hiding.
                 *
                 * The order here is the stack docs/AURORA_MASTER_DESIGN_SYSTEM.md
                 * describes: canvas and artwork atmosphere first, then glass above it,
                 * then content. Glass sampling what is behind it, never itself.
                 */
                val chromeBackdrop = com.kyant.backdrop.backdrops.rememberCombinedBackdrop(
                    backdrop,
                    contentBackdrop
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    /*
                     * The shell's canvas is black, and only black.
                     *
                     * It used to carry a wash of the playing track's dominant colour across
                     * every screen, which is why Home had a brown cast under a photograph of
                     * someone in a red room. The atmosphere belongs to the screen that is
                     * showing the record - Now Playing draws its own - not to the library
                     * list you happen to be scrolling while it plays.
                     */
                    dev.aurora.player.ui.components.AmbientArtworkLayer(
                        modifier = Modifier.layerBackdrop(backdrop),
                        artworkColor = null
                    )

                    AuroraNavigation(
                        navController = navController,
                        container = container,
                        contentBackdrop = contentBackdrop,
                        chromeBackdrop = chromeBackdrop
                    )
                }
            }
        }
    }
}
