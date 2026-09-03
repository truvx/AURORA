package dev.aurora.player.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
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
    AuroraTheme {
        val navController = rememberNavController()
        AuroraNavigation(navController = navController)
    }
}
