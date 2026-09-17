package dev.aurora.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import dev.aurora.player.AuroraApp
import dev.aurora.player.ui.artwork.LocalArtworkLoader
import dev.aurora.player.ui.artwork.rememberArtworkLoader
import dev.aurora.player.ui.artwork.rememberArtworkPalette
import dev.aurora.player.ui.components.LocalPlayerCoordinator
import dev.aurora.player.ui.components.LocalYouTubePlayerAdapter
import dev.aurora.player.ui.navigation.AuroraNavigation
import dev.aurora.player.ui.theme.Aurora
import dev.aurora.player.ui.theme.AuroraMotionTokens
import dev.aurora.player.ui.theme.AuroraTheme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.aurora.player.ui.theme.LocalAuroraBackdrop

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
        LocalYouTubePlayerAdapter provides container.youtubePlayerAdapter,
        LocalArtworkLoader provides artworkLoader,
        dev.aurora.player.ui.accessibility.LocalAccessibilityPreferences provides accessibility
    ) {
        AuroraTheme {
            val navController = rememberNavController()
            val backdrop = rememberLayerBackdrop()

            /*
             * The atmosphere the glass refracts: the dominant colour of whatever is playing.
             *
             * Animated rather than swapped. This fills the screen, and a full-viewport
             * colour change that lands in one frame is an abrupt brightness jump - the thing
             * reduced-motion guidance singles out. The soft family is the slowest one for
             * exactly this, and reduced motion skips the wash entirely.
             */
            val playerState by container.playerCoordinator.state.collectAsState()
            val palette by rememberArtworkPalette(playerState.currentTrack?.artworkUri)

            val target = palette ?: Aurora.colors.backgroundPrimary
            val atmosphere by animateColorAsState(
                targetValue = target,
                animationSpec = if (accessibility.reducedMotion) {
                    snap()
                } else {
                    tween(
                        durationMillis = AuroraMotionTokens.durationAmbient,
                        easing = AuroraMotionTokens.easingStandard
                    )
                },
                label = "artwork atmosphere"
            )

            CompositionLocalProvider(
                LocalAuroraBackdrop provides backdrop
            ) {
                /*
                 * The backdrop layer holds the artwork atmosphere and nothing else.
                 *
                 * It used to wrap the whole navigation, which meant the glass surfaces
                 * inside it sampled the very layer they were drawn into. The render node
                 * then contained itself, and prepareTree recursed until the render thread
                 * blew its stack - a native SIGSEGV at 512 frames, every launch. That is
                 * what the opaque-by-default fallback was really hiding.
                 *
                 * The order here is the stack docs/AURORA_MASTER_DESIGN_SYSTEM.md
                 * describes: canvas and artwork atmosphere first, then glass above it,
                 * then content. Glass sampling what is behind it, never itself.
                 */
                Box(modifier = Modifier.fillMaxSize()) {
                    dev.aurora.player.ui.components.AmbientArtworkLayer(
                        modifier = Modifier.layerBackdrop(backdrop),
                        artworkColor = atmosphere
                    )

                    AuroraNavigation(
                        navController = navController,
                        container = container
                    )
                }
            }
        }
    }
}
