package dev.aurora.player.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.aurora.player.ui.screens.AiScreen
import dev.aurora.player.ui.screens.HomeScreen
import dev.aurora.player.ui.library.LibraryScreen
import dev.aurora.player.ui.screens.SearchScreen
import dev.aurora.player.ui.screens.SettingsScreen
import androidx.compose.foundation.layout.Column

/**
 * AURORA navigation graph with Scaffold shell.
 *
 * Player coordinator lives above screen destinations and survives
 * route changes (Phase 5+). The mini-player region will be added
 * above the bottom bar in later phases.
 */
@Composable
fun AuroraNavigation(
    navController: NavHostController,
    container: dev.aurora.player.app.AppContainer,
    modifier: Modifier = Modifier,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            Column {
                // Phase 6 UI (MiniPlayer, NowPlayingScreen, QueueSheet) is intentionally deferred.
                AuroraBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { destination ->
                        navController.navigate(destination.route) {
                            // Avoid building up a large back stack of tabs
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AuroraDestination.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AuroraDestination.Home.route) { HomeScreen() }
            composable(AuroraDestination.Search.route) { SearchScreen() }
            composable(AuroraDestination.Library.route) { LibraryScreen() }
            composable(AuroraDestination.Ai.route) { 
                val aiViewModel = androidx.compose.runtime.remember {
                    dev.aurora.player.ui.screens.AiViewModel(
                        container.aiProvider,
                        container.aiToolExecutor
                    )
                }
                AiScreen(
                    viewModel = aiViewModel,
                    playerCoordinator = container.playerCoordinator
                ) 
            }
            composable(AuroraDestination.Settings.route) { 
                SettingsScreen(playerCoordinator = container.playerCoordinator) 
            }
        }
    }
}
