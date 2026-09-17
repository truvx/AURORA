package dev.aurora.player.ui.navigation

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
 * The player coordinator lives above screen destinations and survives route changes, and
 * the playback surfaces live here with it rather than inside any one destination:
 * docs/NAVIGATION_ARCHITECTURE.md makes Queue and Now Playing global, "not duplicated per
 * tab", and the mini-player a persistent region.
 *
 * These were written and then left unwired, so playing a track from the library changed
 * nothing on screen - no transport controls, no way to reach the full player, and no queue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuroraNavigation(
    navController: NavHostController,
    container: dev.aurora.player.app.AppContainer,
    modifier: Modifier = Modifier,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Saveable: a rotation must not close the player the user had open.
    var playerExpanded by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(false)
    }
    var queueVisible by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(false)
    }

    // Reduced motion removes the travel, not the transition: the player still arrives and
    // leaves, it just fades rather than sliding the height of the screen.
    val reducedMotion =
        dev.aurora.player.ui.accessibility.LocalAccessibilityPreferences.current.reducedMotion

    // Back closes the player and the queue before it leaves the screen, so neither can trap
    // the user on a surface with no way out.
    androidx.activity.compose.BackHandler(enabled = queueVisible) { queueVisible = false }
    androidx.activity.compose.BackHandler(enabled = playerExpanded && !queueVisible) {
        playerExpanded = false
    }

    androidx.compose.foundation.layout.Box(modifier = modifier) {
    Scaffold(
        bottomBar = {
            Column {
                // Hidden while the full player is open. They are two states of one surface,
                // so showing both at once duplicates every transport control in the
                // accessibility tree - a screen reader finds two "Play" buttons, one of
                // them behind a screen the user cannot see past.
                if (!playerExpanded) {
                    dev.aurora.player.ui.components.MiniPlayer(
                        onExpand = { playerExpanded = true }
                    )
                }
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
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AuroraDestination.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AuroraDestination.Home.route) { HomeScreen() }
            composable(AuroraDestination.Search.route) { SearchScreen() }
            composable(AuroraDestination.Library.route) {
                val libraryViewModel = androidx.compose.runtime.remember {
                    dev.aurora.player.ui.library.LibraryViewModel(
                        container.localLibraryProvider,
                        container.libraryOrganizationRepository
                    )
                }
                val items by libraryViewModel.items.collectAsState()
                val favoriteIds by libraryViewModel.favoriteIds.collectAsState()
                val playlists by libraryViewModel.playlists.collectAsState()
                val openPlaylistId by libraryViewModel.openPlaylistId.collectAsState()
                val openPlaylistTracks by libraryViewModel.openPlaylistTracks.collectAsState()
                LibraryScreen(
                    items = items,
                    favoriteIds = favoriteIds,
                    playlists = playlists,
                    openPlaylistId = openPlaylistId,
                    openPlaylistTracks = openPlaylistTracks,
                    onScanRequested = libraryViewModel::scan,
                    onToggleFavorite = libraryViewModel::setFavorite,
                    onPlayTrack = { item ->
                        container.playerCoordinator.dispatch(
                            dev.aurora.player.domain.player.PlayerCommand.Load(item)
                        )
                        container.playerCoordinator.dispatch(
                            dev.aurora.player.domain.player.PlayerCommand.Play
                        )
                    },
                    onOpenPlaylist = libraryViewModel::openPlaylist,
                    onCreatePlaylist = libraryViewModel::createPlaylist,
                    onDeletePlaylist = libraryViewModel::deletePlaylist,
                    onAddToPlaylist = libraryViewModel::addToPlaylist,
                    onRemoveEntry = libraryViewModel::removeFromPlaylist,
                    onMoveEntry = libraryViewModel::moveEntry
                )
            }
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
                val navContext = androidx.compose.ui.platform.LocalContext.current
                val privacyViewModel = androidx.compose.runtime.remember {
                    dev.aurora.player.ui.screens.PrivacyViewModel(
                        container.libraryOrganizationRepository,
                        dev.aurora.player.ui.screens.PrivacyViewModel.exportDirFor(navContext)
                    )
                }
                SettingsScreen(
                    playerCoordinator = container.playerCoordinator,
                    privacyViewModel = privacyViewModel
                )
            }
        }
    }

    /*
     * The full player, over the shell rather than as a destination: it is a playback
     * surface, not a tab, and routing to it would put it in the back stack behind whatever
     * the user was browsing.
     *
     * Enter and exit travel the same path - up from the bottom, back down to it - matching
     * the direction the mini-player is dragged to open it. A surface that arrives one way
     * and leaves another breaks the relationship between the two states.
     */
    androidx.compose.animation.AnimatedVisibility(
        visible = playerExpanded,
        enter = if (reducedMotion) {
            androidx.compose.animation.fadeIn()
        } else {
            androidx.compose.animation.slideInVertically(
                initialOffsetY = { it },
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = dev.aurora.player.ui.theme.AuroraMotionTokens.springStandardDamping,
                    stiffness = dev.aurora.player.ui.theme.AuroraMotionTokens.springStandardStiffness,
                    visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold
                )
            )
        },
        exit = if (reducedMotion) {
            androidx.compose.animation.fadeOut()
        } else {
            androidx.compose.animation.slideOutVertically(
                targetOffsetY = { it },
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = dev.aurora.player.ui.theme.AuroraMotionTokens.springStandardDamping,
                    stiffness = dev.aurora.player.ui.theme.AuroraMotionTokens.springStandardStiffness,
                    visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold
                )
            )
        }
    ) {
        dev.aurora.player.ui.screens.NowPlayingScreen(
            onClose = { playerExpanded = false },
            onOpenQueue = { queueVisible = true }
        )
    }

    if (queueVisible) {
        // A real modal sheet: it brings its own scrim, drag handle, velocity-aware settling
        // and dismiss gesture, and it is already wired for back and for screen readers.
        // The queue was previously a plain full-screen column with none of that.
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { queueVisible = false },
            containerColor = dev.aurora.player.ui.theme.Aurora.colors.backgroundPrimary
        ) {
            dev.aurora.player.ui.screens.QueueSheet(onClose = { queueVisible = false })
        }
    }
    }
}
