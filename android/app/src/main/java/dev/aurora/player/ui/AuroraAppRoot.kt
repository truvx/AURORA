package dev.aurora.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import dev.aurora.player.AuroraApp
import dev.aurora.player.ui.components.LocalPlayerCoordinator
import dev.aurora.player.ui.components.LocalYouTubePlayerAdapter
import dev.aurora.player.ui.navigation.AuroraNavigation
import dev.aurora.player.ui.theme.AuroraTheme

import androidx.compose.foundation.layout.Box
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

    CompositionLocalProvider(
        LocalPlayerCoordinator provides container.playerCoordinator,
        LocalYouTubePlayerAdapter provides container.youtubePlayerAdapter,
        dev.aurora.player.ui.accessibility.LocalAccessibilityPreferences provides accessibility
    ) {
        AuroraTheme {
            val navController = rememberNavController()
            val backdrop = rememberLayerBackdrop()
            CompositionLocalProvider(
                LocalAuroraBackdrop provides backdrop
            ) {
                dev.aurora.player.ui.components.AmbientArtworkLayer {
                    Box(modifier = Modifier.layerBackdrop(backdrop)) {
                        AuroraNavigation(
                            navController = navController,
                            container = container
                        )
                    }
                }
            }
        }
    }
}
