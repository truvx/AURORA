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

    CompositionLocalProvider(
        LocalPlayerCoordinator provides container.playerCoordinator,
        LocalYouTubePlayerAdapter provides container.youtubePlayerAdapter
    ) {
        AuroraTheme {
            val navController = rememberNavController()
            AuroraNavigation(navController = navController)
        }
    }
}
