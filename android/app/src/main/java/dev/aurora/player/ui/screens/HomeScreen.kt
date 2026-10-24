package dev.aurora.player.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.ui.components.GlassCard
import dev.aurora.player.ui.components.GlassLevel
import dev.aurora.player.ui.components.ArtistCard
import dev.aurora.player.ui.components.ReleaseCard
import dev.aurora.player.ui.components.SectionHeader
import dev.aurora.player.ui.components.TrackRow
import dev.aurora.player.ui.navigation.LocalChromeInsets
import dev.aurora.player.ui.theme.Aurora

/**
 * AURORA Home.
 *
 * Three sections, each built from the listener's own history: what the artists they play
 * have released, mixes seeded with those artists, and what they have been listening to.
 * Nothing here is filler - a section that has nothing real to show says why instead.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel? = null,
    /**
     * Play a list from one of its tracks.
     *
     * The list rather than the one item, so the queue has somewhere for next and previous
     * to go. Tapping a row means "play this list from here".
     */
    onPlay: (List<MediaItem>, Int) -> Unit = { _, _ -> },
    onOpenArtist: (String) -> Unit = {},
    onAddToQueue: (MediaItem) -> Unit = {},
    onOpenWhatsNew: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    /**
     * Open the library at one of its views, optionally narrowed to favourites.
     *
     * Every tile leads somewhere that already exists. A grid of six that includes two
     * destinations the app does not have would look like the reference and behave like a
     * dead end, which is worse than five tiles.
     */
    onOpenLibrary: (dev.aurora.player.ui.library.LibraryView, Boolean) -> Unit = { _, _ -> },
    /** Open one playlist, in the library, where the rest of them live. */
    onOpenPlaylist: (String) -> Unit = {},
    /**
     * Preferred Audio Quality, which is a request and not a promise.
     *
     * Home offers the three that make sense to change between listening contexts; Auto,
     * Low and Hi-Res live in Settings, where there is room to say what each one means.
     * Nothing reads this to describe a track - Now Playing discloses what actually played.
     */
    preferredQuality: dev.aurora.player.domain.audio.RequestedQuality =
        dev.aurora.player.domain.audio.RequestedQuality.Auto,
    onSelectQuality: (dev.aurora.player.domain.audio.RequestedQuality) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val spacing = Aurora.spacing
    val typography = Aurora.typography
    val colors = Aurora.colors

    val releases by (viewModel?.releases?.collectAsState()
        ?: androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf<ReleasesState>(ReleasesState.NoHistory)
        })
    val artists by (viewModel?.artists?.collectAsState()
        ?: androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(emptyList<TopArtist>())
        })
    val recentlyPlayed by (viewModel?.recentlyPlayed?.collectAsState()
        ?: androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf<List<MediaItem>?>(emptyList())
        })
    val playlists by (viewModel?.playlists?.collectAsState()
        ?: androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf<List<dev.aurora.player.domain.library.Playlist>?>(emptyList())
        })
    val hasUnseen = (releases as? ReleasesState.Ready)?.unseen?.isNotEmpty() == true
    val favorites by (viewModel?.favoriteIds?.collectAsState()
        ?: androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(emptySet<String>())
        })

    LazyColumn(
        modifier = modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(
            top = spacing.space6,
            bottom = LocalChromeInsets.current.calculateBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.space6)
    ) {
        item {
            /*
             * Controls, not a title.
             *
             * The screen names itself well enough through what is on it, and the reference
             * spends the top row on the two things a listener reaches for - finding
             * something, and how good it should sound - rather than on a word they have
             * already read. What's new moved into the Latest Releases header, beside the
             * thing it is about.
             */
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.space4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space3),
            ) {
                dev.aurora.player.ui.components.GlassIconButton(
                    icon = Icons.Rounded.Search,
                    contentDesc = "Search",
                    onClick = onOpenSearch,
                )

                dev.aurora.player.ui.components.SegmentedControl(
                    options = dev.aurora.player.domain.audio.RequestedQuality.onHome,
                    selected = preferredQuality,
                    label = { it.label },
                    onSelect = onSelectQuality,
                    modifier = Modifier.weight(1f),
                )

                dev.aurora.player.ui.components.GlassIconButton(
                    icon = Icons.Rounded.Settings,
                    contentDesc = "Settings",
                    onClick = onOpenSettings,
                )
            }
        }

        // --- Quick access ---------------------------------------------------------------
        item {
            /*
             * Six ways in, above the feed.
             *
             * Two columns rather than three: these are labelled destinations, not icons,
             * and at a large text size a third column turns "New releases" into "New…".
             * A LazyVerticalGrid cannot live inside a LazyColumn - it has no height to
             * measure against - so this is three plain rows, which is what six items need.
             */
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.space4),
                verticalArrangement = Arrangement.spacedBy(spacing.space2)
            ) {
                val destinations = listOf(
                    QuickDestination(Icons.Rounded.Favorite, "Liked songs") {
                        onOpenLibrary(dev.aurora.player.ui.library.LibraryView.Tracks, true)
                    },
                    QuickDestination(Icons.AutoMirrored.Outlined.QueueMusic, "Playlists") {
                        onOpenLibrary(dev.aurora.player.ui.library.LibraryView.Playlists, false)
                    },
                    QuickDestination(Icons.Outlined.Album, "Albums") {
                        onOpenLibrary(dev.aurora.player.ui.library.LibraryView.Albums, false)
                    },
                    QuickDestination(Icons.Outlined.Person, "Artists") {
                        onOpenLibrary(dev.aurora.player.ui.library.LibraryView.Artists, false)
                    },
                    QuickDestination(Icons.Outlined.MusicNote, "Songs") {
                        onOpenLibrary(dev.aurora.player.ui.library.LibraryView.Tracks, false)
                    },
                    QuickDestination(
                        if (hasUnseen) Icons.Rounded.NotificationsActive else Icons.Rounded.Notifications,
                        "New releases",
                        onOpenWhatsNew
                    ),
                )
                destinations.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                        pair.forEach { destination ->
                            QuickTile(
                                icon = destination.icon,
                                label = destination.label,
                                onClick = destination.onClick
                            )
                        }
                    }
                }
            }
        }

        // --- Latest releases ------------------------------------------------------------
        item {
            Column {
                SectionHeader(
                    title = "Latest Releases",
                    icon = Icons.Outlined.Album,
                    // What's new sits beside the thing it is about, rather than as a bell
                    // in a corner that could mean anything. The label says when there is
                    // something unread, because a dot alone is colour-only status.
                    actionLabel = if (hasUnseen) "New" else "See all",
                    onAction = onOpenWhatsNew,
                    modifier = Modifier.padding(horizontal = spacing.space4)
                )
                when (val state = releases) {
                    is ReleasesState.Ready -> {
                        if (state.releases.isEmpty()) {
                            Notice(
                                icon = Icons.Outlined.Album,
                                headline = "Nothing new",
                                detail = "The artists you play haven't released anything in the last six months."
                            )
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = spacing.space4),
                                horizontalArrangement = Arrangement.spacedBy(spacing.space3)
                            ) {
                                itemsIndexed(state.releases, key = { _, it -> it.release.item.id }) { index, release ->
                                    ReleaseCard(
                                        title = release.title,
                                        artist = release.artist,
                                        coverUrl = release.coverUrl,
                                        isNew = release.release.item.id in state.unseen,
                                        onClick = {
                                            onPlay(state.releases.map { it.release.item }, index)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    // The shape of what is coming, rather than a sentence about waiting.
                    ReleasesState.Loading -> dev.aurora.player.ui.components.CardRowSkeleton(
                        modifier = Modifier.padding(horizontal = spacing.space4),
                        contentDescription = "Looking for new releases"
                    )
                    /*
                     * Each of these is a different problem with a different answer, so each
                     * says which one it is - and where there is something to do about it,
                     * it is a button rather than directions to a screen.
                     */
                    ReleasesState.NoHistory -> Notice(
                        icon = Icons.Outlined.Album,
                        headline = "Play something first",
                        detail = "New releases from the artists you play appear here.",
                        actionLabel = "Find music",
                        onAction = onOpenSearch
                    )
                    ReleasesState.NotConfigured -> Notice(
                        icon = Icons.Rounded.Settings,
                        headline = "No YouTube key yet",
                        detail = "New releases come from the YouTube Data API, which needs a key of your own.",
                        actionLabel = "Open Settings",
                        onAction = onOpenSettings
                    )
                    ReleasesState.Unavailable -> Notice(
                        icon = Icons.Outlined.CloudOff,
                        headline = "Couldn't reach YouTube",
                        detail = "Check your connection. Nothing else on this page needs it.",
                        actionLabel = "Try again",
                        onAction = { viewModel?.retryReleases() },
                        announce = true
                    )
                }
            }
        }

        // --- Playlists --------------------------------------------------------------------
        // Nothing until the table has been read: an empty list and an unread one are
        // different things, and showing "no playlists" for a frame is the wrong one.
        playlists?.let { made ->
            item {
                Column {
                    SectionHeader(
                        title = "Playlists",
                        icon = Icons.AutoMirrored.Outlined.QueueMusic,
                        // Countable, so counted. The reference also totals their running
                        // time; AURORA does not know every track's duration, and a total
                        // that quietly omits the unknown ones is a wrong number.
                        subtitle = "${made.size} ${if (made.size == 1) "playlist" else "playlists"}",
                        actionLabel = "See all",
                        onAction = {
                            onOpenLibrary(dev.aurora.player.ui.library.LibraryView.Playlists, false)
                        },
                        modifier = Modifier.padding(horizontal = spacing.space4)
                    )
                    if (made.isEmpty()) {
                        Notice(
                            icon = Icons.AutoMirrored.Outlined.QueueMusic,
                            headline = "No playlists yet",
                            detail = "Make one in your library, then add tracks to it from anywhere.",
                            actionLabel = "Open Playlists",
                            onAction = {
                                onOpenLibrary(dev.aurora.player.ui.library.LibraryView.Playlists, false)
                            }
                        )
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = spacing.space4),
                            horizontalArrangement = Arrangement.spacedBy(spacing.space3)
                        ) {
                            items(made, key = { it.id }) { playlist ->
                                dev.aurora.player.ui.components.PlaylistCard(
                                    name = playlist.name,
                                    trackCount = playlist.trackCount,
                                    onClick = { onOpenPlaylist(playlist.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- The artists behind it --------------------------------------------------------
        if (artists.isNotEmpty()) {
            item {
                Column {
                    SectionHeader(
                        title = "Your Artists",
                        icon = Icons.Outlined.Person,
                        // Not a count of the library's artists: this row is the handful the
                        // listener actually plays, and saying "8" about it would describe
                        // the row's length rather than anything about them.
                        subtitle = "The people behind what you play",
                        actionLabel = "See all",
                        onAction = {
                            onOpenLibrary(dev.aurora.player.ui.library.LibraryView.Artists, false)
                        },
                        modifier = Modifier.padding(horizontal = spacing.space4)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = spacing.space4),
                        horizontalArrangement = Arrangement.spacedBy(spacing.space3)
                    ) {
                        items(artists, key = { it.name }) { artist ->
                            ArtistCard(
                                name = artist.name,
                                artworkUri = artist.artworkUri,
                                onClick = { onOpenArtist(artist.name) }
                            )
                        }
                    }
                }
            }
        }

        // --- Recently played ------------------------------------------------------------
        recentlyPlayed?.let { played ->
            item {
                SectionHeader(
                    title = "Recently Played",
                    icon = Icons.Outlined.History,
                    subtitle = if (played.isEmpty()) null else "Private to this device",
                    modifier = Modifier.padding(horizontal = spacing.space4)
                )
            }
            if (played.isEmpty()) {
                item {
                    Notice(
                        icon = Icons.Outlined.History,
                        headline = "Nothing played yet",
                        detail = "What you listen to is kept on this device, and you can clear it at any time.",
                        actionLabel = "Find music",
                        onAction = onOpenSearch
                    )
                }
            } else {
                // One item, so the spacing between sections does not also push the rows apart.
                item {
                    Column {
                        played.forEach { item ->
                            TrackRow(
                                item = item,
                                onPlay = { onPlay(played, played.indexOf(item)) },
                                isFavorite = item.id in favorites,
                                onToggleFavorite = { viewModel?.setFavorite(item.id, item.id !in favorites) },
                                onAddToQueue = { onAddToQueue(item) },
                                modifier = Modifier.padding(horizontal = spacing.space4)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One of the six ways into the library from the top of Home. */
private data class QuickDestination(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * A quick-access tile: an icon, a word, and somewhere real to go.
 *
 * It answers on touch-down rather than on release - the glass bends under the finger and
 * the haptic fires with it, so the tile is responding while the press is still happening.
 */
@Composable
private fun RowScope.QuickTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val interaction = androidx.compose.runtime.remember {
        androidx.compose.foundation.interaction.MutableInteractionSource()
    }
    val press by dev.aurora.player.ui.components.rememberPressProgress(interaction)
    val haptics = dev.aurora.player.ui.haptics.LocalHapticEngine.current

    dev.aurora.player.ui.components.GlassSurface(
        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
        level = GlassLevel.Secondary,
        pressProgress = { press },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = androidx.compose.ui.semantics.Role.Button,
                ) {
                    haptics.fire(dev.aurora.player.ui.haptics.HapticEvent.Tap)
                    onClick()
                }
                .padding(Aurora.spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Aurora.colors.accentPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(Aurora.spacing.space3))
            Text(
                text = label,
                style = Aurora.typography.label,
                color = Aurora.colors.textPrimary,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/** A section's empty, loading-failed or unconfigured state, inset to the page's margin. */
@Composable
private fun Notice(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    headline: String,
    detail: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    announce: Boolean = false,
) {
    dev.aurora.player.ui.components.SectionNotice(
        icon = icon,
        headline = headline,
        detail = detail,
        actionLabel = actionLabel,
        onAction = onAction,
        announce = announce,
        modifier = Modifier.padding(horizontal = Aurora.spacing.space4)
    )
}
