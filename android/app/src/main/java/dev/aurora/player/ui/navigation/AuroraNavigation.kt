package dev.aurora.player.ui.navigation

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.layout.fillMaxSize
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
import kotlinx.coroutines.launch
import com.kyant.backdrop.backdrops.layerBackdrop
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
/** A screen reached from the header rather than the navigation pill. */
private const val WHATS_NEW_ROUTE = "whats_new"

/**
 * Play a whole list: the first track loads and starts, the rest queue behind it.
 *
 * Shuffling happens here rather than by turning shuffle mode on, so "Shuffle" plays these
 * tracks in a random order without changing what the player does when the queue advances
 * afterwards.
 */
private fun playAll(
    container: dev.aurora.player.app.AppContainer,
    tracks: List<dev.aurora.player.domain.models.MediaItem>,
    shuffled: Boolean
) {
    val ordered = if (shuffled) tracks.shuffled() else tracks
    val first = ordered.firstOrNull() ?: return
    playNow(container, first)
    ordered.drop(1).forEach { track ->
        container.playerCoordinator.dispatch(
            dev.aurora.player.domain.player.PlayerCommand.AddToQueue(track)
        )
    }
}

/** Load a track and start it: the two commands every "play this" in the UI sends. */
private fun playNow(container: dev.aurora.player.app.AppContainer, item: dev.aurora.player.domain.models.MediaItem) {
    container.playerCoordinator.dispatch(dev.aurora.player.domain.player.PlayerCommand.Load(item))
    container.playerCoordinator.dispatch(dev.aurora.player.domain.player.PlayerCommand.Play)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuroraNavigation(
    navController: NavHostController,
    container: dev.aurora.player.app.AppContainer,
    contentBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null,
    chromeBackdrop: com.kyant.backdrop.Backdrop? = null,
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

    // One way to switch tabs, shared by the bottom bar and by screens that send the user
    // elsewhere (the AI screen pointing at Settings), so both keep the same back stack.
    fun navigateToTab(destination: AuroraDestination) {
        navController.navigate(destination.route) {
            // Avoid building up a large back stack of tabs
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    /*
     * No Scaffold: the chrome floats over the content rather than reserving a strip of the
     * window, which is what lets the page pass under the glass and refract through it.
     * Screens make room for it themselves via LocalChromeInsets, so the last row can scroll
     * clear instead of sitting under the bar forever.
     */
    val chromeInsets = rememberChromeInsets()

    // One instance for the whole shell: Home and What's New show the same releases, and
    // building a second view model would fetch them twice.
    val homeViewModel = androidx.compose.runtime.remember {
        dev.aurora.player.ui.screens.HomeViewModel(
            organization = container.libraryOrganizationRepository,
            resolver = container.mediaResolver,
            releasesRepository = container.latestReleasesRepository,
            whatsNew = container.whatsNewStore
        )
    }

    androidx.compose.foundation.layout.Box(
        modifier = modifier.fillMaxSize()
    ) {
    androidx.compose.runtime.CompositionLocalProvider(LocalChromeInsets provides chromeInsets) {
        androidx.compose.foundation.layout.Box(
            modifier = (if (contentBackdrop != null) {
                Modifier.layerBackdrop(contentBackdrop)
            } else {
                Modifier
            }).fillMaxSize()
        ) {
        NavHost(
            navController = navController,
            startDestination = AuroraDestination.Home.route,
        ) {
            composable(AuroraDestination.Home.route) {
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                val profile by container.profileStore.profile.collectAsState()
                HomeScreen(
                    viewModel = homeViewModel,
                    profile = profile,
                    onOpenWhatsNew = { navController.navigate(WHATS_NEW_ROUTE) },
                    onOpenProfile = { navigateToTab(AuroraDestination.Settings) },
                    onPlay = { item -> playNow(container, item) },
                    onAddToQueue = { item ->
                        container.playerCoordinator.dispatch(
                            dev.aurora.player.domain.player.PlayerCommand.AddToQueue(item)
                        )
                    },
                    onPlayMix = { mix ->
                        scope.launch {
                            // A mix is a search that has not run yet: it is generated when
                            // the listener asks for it, not kept warm in the background.
                            val results = container.recommendationEngine
                                .getRecommendations(mix.query)
                                .map { it.item }
                            if (results.isNotEmpty()) {
                                playNow(container, results.first())
                                results.drop(1).forEach { track ->
                                    container.playerCoordinator.dispatch(
                                        dev.aurora.player.domain.player.PlayerCommand.AddToQueue(track)
                                    )
                                }
                            }
                        }
                    }
                )
            }
            composable(AuroraDestination.Search.route) {
                val searchViewModel = androidx.compose.runtime.remember {
                    dev.aurora.player.ui.screens.SearchViewModel(
                        localProvider = container.localLibraryProvider,
                        youtubeProvider = container.youtubeMusicProvider,
                        trending = container.trendingRepository
                    )
                }
                SearchScreen(
                    viewModel = searchViewModel,
                    onPlay = { item -> playNow(container, item) },
                    onAddToQueue = { item ->
                        container.playerCoordinator.dispatch(
                            dev.aurora.player.domain.player.PlayerCommand.AddToQueue(item)
                        )
                    }
                )
            }
            composable(WHATS_NEW_ROUTE) {
                val releasesState by homeViewModel.releases.collectAsState()
                val ready = releasesState as? dev.aurora.player.ui.screens.ReleasesState.Ready
                dev.aurora.player.ui.screens.WhatsNewScreen(
                    releases = ready?.releases.orEmpty(),
                    unseen = ready?.unseen.orEmpty(),
                    onPlay = { item -> playNow(container, item) },
                    onBack = { navController.popBackStack() },
                    onSeen = homeViewModel::markReleasesSeen
                )
            }
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
                    onMoveEntry = libraryViewModel::moveEntry,
                    onPlayTracks = { tracks, shuffled -> playAll(container, tracks, shuffled) },
                    onQueueTracks = { tracks ->
                        tracks.forEach { track ->
                            container.playerCoordinator.dispatch(
                                dev.aurora.player.domain.player.PlayerCommand.AddToQueue(track)
                            )
                        }
                    }
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
                    playerCoordinator = container.playerCoordinator,
                    onOpenSettings = { navigateToTab(AuroraDestination.Settings) }
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
                    privacyViewModel = privacyViewModel,
                    gatewayTokenStore = container.gatewayTokenStore,
                    youTubeKeyStore = container.youTubeKeyStore,
                    profileStore = container.profileStore
                )
            }
        }
        }

        // Drawn after the captured content layer, never inside it: the chrome samples that
        // layer, and a surface that samples the layer it lives in recurses forever.
        if (!playerExpanded) {
            AuroraFloatingChrome(
                currentRoute = currentRoute,
                onNavigate = ::navigateToTab,
                onExpandPlayer = { playerExpanded = true },
                backdrop = chromeBackdrop,
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
            )
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
