package dev.aurora.player.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.aurora.player.domain.library.Playlist
import dev.aurora.player.domain.library.PlaylistTrack
import dev.aurora.player.domain.library.TrackGroup
import dev.aurora.player.domain.library.TrackGrouping
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.ui.components.GlassButton
import dev.aurora.player.ui.components.GlassButtonStyle
import dev.aurora.player.ui.components.GlassLevel
import dev.aurora.player.ui.components.GlassIconButton
import dev.aurora.player.ui.components.GlassSurface
import dev.aurora.player.ui.haptics.HapticEvent
import dev.aurora.player.ui.haptics.LocalHapticEngine
import dev.aurora.player.ui.theme.Aurora

@Composable
// No default arguments: this screen was shipped twice with unwired callbacks that silently
// did nothing, because a missing argument fell back to a no-op lambda. Requiring every
// parameter turns that mistake into a compile error.
fun LibraryScreen(
    items: List<MediaItem>,
    favoriteIds: Set<String>,
    playlists: List<Playlist>,
    /** Which of the four lists is on screen; owned by the view model, not by this screen. */
    view: LibraryView,
    favoritesOnly: Boolean,
    openGroupKey: String?,
    openPlaylistId: String?,
    openPlaylistTracks: List<PlaylistTrack>,
    onSelectView: (LibraryView) -> Unit,
    onSetFavoritesOnly: (Boolean) -> Unit,
    onOpenGroup: (String?) -> Unit,
    onScanRequested: () -> Unit,
    onToggleFavorite: (mediaId: String, isFavorite: Boolean) -> Unit,
    /**
     * Play a list from one of its tracks.
     *
     * The list rather than the one item, so the queue has somewhere for next and previous
     * to go. The list is whatever is visible - the favourites filter and the open album
     * are the context, so they are what queues.
     */
    onPlayFrom: (List<MediaItem>, Int) -> Unit,
    onOpenPlaylist: (String?) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onAddToPlaylist: (playlistId: String, mediaId: String) -> Unit,
    onRemoveEntry: (playlistId: String, entryId: String) -> Unit,
    onMoveEntry: (playlistId: String, from: Int, to: Int) -> Unit,
    onPlayTracks: (tracks: List<MediaItem>, shuffled: Boolean) -> Unit = { _, _ -> },
    onQueueTracks: (tracks: List<MediaItem>) -> Unit = { },
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticEngine.current
    var pendingAddTrackId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    // Read the real permission state; a permission granted in an earlier session must not
    // leave the user stuck behind the request gate.
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            onScanRequested()
        }
    }

    // Pick up files added since the last visit when access is already in place.
    LaunchedEffect(hasPermission) {
        if (hasPermission) onScanRequested()
    }

    val spacing = Aurora.spacing
    val typography = Aurora.typography
    val colors = Aurora.colors

    // An album's tracks and a playlist's tracks are pages, so back leaves the page rather
    // than the library. Without this the only way out of one is the button beside its title.
    androidx.activity.compose.BackHandler(enabled = openGroupKey != null) { onOpenGroup(null) }
    androidx.activity.compose.BackHandler(
        enabled = view == LibraryView.Playlists && openPlaylistId != null
    ) { onOpenPlaylist(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = spacing.space4)
            // Top clears the status bar; bottom is the room the floating chrome needs, so
            // the last row scrolls clear of the glass instead of sitting under it forever.
            .padding(
                top = spacing.space6,
                bottom = dev.aurora.player.ui.navigation.LocalChromeInsets.current
                    .calculateBottomPadding()
            )
    ) {
        if (!hasPermission) {
            Text(
                text = "Library",
                style = typography.display,
                color = colors.textPrimary,
                modifier = Modifier.padding(bottom = spacing.space6)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.space8),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.LibraryMusic,
                    contentDescription = null,
                    tint = colors.accentPrimary.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = spacing.space4)
                )
                Text(
                    text = "Your library is waiting",
                    style = typography.title,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = spacing.space2)
                )
                Text(
                    text = "Allow AURORA to scan your device for local music files.",
                    style = typography.body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = spacing.space6)
                )
                GlassButton(
                    text = "Allow Access",
                    onClick = { permissionLauncher.launch(audioPermission) }
                )
            }
            return@Column
        }

        // A playlist, or an album's tracks, is a page of its own, as in the references:
        // the library title and the view switch belong to the list behind it.
        val grouped = view == LibraryView.Albums || view == LibraryView.Artists
        val inDetail = when (view) {
            LibraryView.Playlists -> openPlaylistId != null
            LibraryView.Albums, LibraryView.Artists -> openGroupKey != null
            LibraryView.Tracks -> false
        }

        if (!inDetail) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space4),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Library",
                    style = typography.display,
                    color = colors.textPrimary
                )
                GlassButton(
                    text = "Rescan",
                    onClick = onScanRequested,
                    style = GlassButtonStyle.Secondary
                )
            }

            ViewSwitch(
                view = view,
                onSelect = onSelectView,
                modifier = Modifier.padding(bottom = spacing.space4)
            )
        }

        if (view == LibraryView.Playlists) {
            PlaylistsSection(
                playlists = playlists,
                openPlaylist = playlists.firstOrNull { it.id == openPlaylistId },
                openPlaylistTracks = openPlaylistTracks,
                onOpenPlaylist = onOpenPlaylist,
                onCreatePlaylist = onCreatePlaylist,
                onDeletePlaylist = onDeletePlaylist,
                onRemoveEntry = onRemoveEntry,
                onMoveEntry = onMoveEntry,
                onPlayTracks = onPlayTracks,
                onQueueTracks = onQueueTracks
            )
            return@Column
        }

        if (grouped) {
            // Derived when asked rather than stored: a second copy in the database would be
            // one more thing to keep in step with a rescan.
            val groups = remember(items, view) {
                if (view == LibraryView.Albums) {
                    TrackGrouping.byAlbum(items)
                } else {
                    TrackGrouping.byArtist(items)
                }
            }
            GroupedTracks(
                groups = groups,
                tracks = items,
                openGroupKey = openGroupKey,
                label = view.singular,
                onOpen = onOpenGroup,
                onPlayFrom = onPlayFrom,
                modifier = Modifier.weight(1f)
            )
            return@Column
        }

        // --- Tracks -----------------------------------------------------------------------

        // The visible list is the context, so the filter decides what queues as well as
        // what is shown.
        val visibleTracks = remember(items, favoritesOnly, favoriteIds) {
            if (favoritesOnly) items.filter { it.id in favoriteIds } else items
        }

        if (items.isNotEmpty()) {
            GlassButton(
                text = if (favoritesOnly) "Showing favourites" else "Show favourites only",
                onClick = { onSetFavoritesOnly(!favoritesOnly) },
                style = if (favoritesOnly) GlassButtonStyle.Primary else GlassButtonStyle.Secondary,
                modifier = Modifier
                    .padding(bottom = spacing.space3)
                    // Pressed state, not just a colour: the label says which it is, and the
                    // semantics say so to a screen reader.
                    .semantics { selected = favoritesOnly }
            )
        }

        pendingAddTrackId?.let { trackId ->
            AddToPlaylistRow(
                playlists = playlists,
                onPick = { playlistId ->
                    onAddToPlaylist(playlistId, trackId)
                    pendingAddTrackId = null
                },
                onDismiss = { pendingAddTrackId = null },
                modifier = Modifier.padding(bottom = spacing.space3)
            )
        }

        if (visibleTracks.isEmpty()) {
            EmptyState(
                icon = if (favoritesOnly) Icons.Outlined.FavoriteBorder else Icons.Outlined.Search,
                title = if (favoritesOnly) "No favourites yet" else "No music found",
                detail = if (favoritesOnly) {
                    "Tap the heart on a track to keep it here."
                } else {
                    "Add audio files to your device or initiate a rescan."
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.space2)
            ) {
                items(visibleTracks, key = { it.id }) { item ->
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        level = GlassLevel.Secondary
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = item.isAvailable) {
                                    haptics.fire(HapticEvent.Tap)
                                    onPlayFrom(visibleTracks, visibleTracks.indexOf(item))
                                }
                                .padding(spacing.space3),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            dev.aurora.player.ui.components.ArtworkSurface(
                                // Named, so a screen reader announces which album's art
                                // this is rather than "Album art" on every row.
                                altText = "${item.title} artwork",
                                artworkUri = item.artworkUri,
                                modifier = Modifier.height(48.dp)
                            )
                            Column(
                                modifier = Modifier.padding(start = spacing.space3).weight(1f)
                            ) {
                                Text(
                                    text = item.title,
                                    style = typography.trackTitle,
                                    color = if (item.isAvailable) colors.textPrimary else colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.artist ?: TrackGrouping.UNKNOWN_ARTIST,
                                    style = typography.trackArtistCompact,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            GlassIconButton(
                                icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                                contentDesc = "Add ${item.title} to a playlist",
                                onClick = {
                                    haptics.fire(HapticEvent.Selection)
                                    pendingAddTrackId = item.id
                                }
                            )

                            val isFavorite = favoriteIds.contains(item.id)
                            GlassIconButton(
                                icon = if (isFavorite) {
                                    Icons.Filled.Favorite
                                } else {
                                    Icons.Outlined.FavoriteBorder
                                },
                                contentDesc = if (isFavorite) {
                                    "Remove ${item.title} from favorites"
                                } else {
                                    "Add ${item.title} to favorites"
                                },
                                onClick = {
                                    haptics.fire(HapticEvent.Favorite)
                                    onToggleFavorite(item.id, !isFavorite)
                                },
                                selected = isFavorite
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The four lists, as one row.
 *
 * It scrolls rather than squeezing: four labels do not fit across a small phone at a large
 * text size, and a label clipped to three letters is worse than one the user swipes to.
 */
@Composable
private fun ViewSwitch(
    view: LibraryView,
    onSelect: (LibraryView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticEngine.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .semantics { role = Role.Tab },
        horizontalArrangement = Arrangement.spacedBy(Aurora.spacing.space2)
    ) {
        LibraryView.entries.forEach { candidate ->
            val selected = candidate == view
            GlassButton(
                text = candidate.label,
                onClick = {
                    if (!selected) {
                        haptics.fire(HapticEvent.Selection)
                        onSelect(candidate)
                    }
                },
                style = if (selected) GlassButtonStyle.Primary else GlassButtonStyle.Secondary,
                // Filled versus quiet says which is current; this says it out loud.
                modifier = Modifier.semantics { this.selected = selected }
            )
        }
    }
}

/**
 * Albums or artists: the list of them, or the tracks inside one.
 *
 * Opening a group and playing from it are the same two gestures the Web client has, and
 * the group is the context - tapping its third track queues the whole group from there.
 */
@Composable
private fun GroupedTracks(
    groups: List<TrackGroup>,
    tracks: List<MediaItem>,
    openGroupKey: String?,
    label: String,
    onOpen: (String?) -> Unit,
    onPlayFrom: (List<MediaItem>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = Aurora.spacing
    val typography = Aurora.typography
    val colors = Aurora.colors

    if (groups.isEmpty()) {
        EmptyState(
            icon = if (label == "artist") Icons.Outlined.Person else Icons.Outlined.Album,
            title = "No ${label}s yet",
            detail = "Scan a music folder to see your ${label}s here."
        )
        return
    }

    // Re-read from the current grouping rather than from a snapshot taken when the group
    // was opened, so a rescan is reflected instead of frozen.
    val open = groups.firstOrNull { it.key == openGroupKey }

    if (open != null) {
        val byId = remember(tracks) { tracks.associateBy { it.id } }
        val inGroup = remember(open, byId) { open.trackIds.mapNotNull(byId::get) }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space4),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDesc = "Back to all ${label}s",
                onClick = { onOpen(null) }
            )
            Column(modifier = Modifier.padding(start = spacing.space3)) {
                Text(
                    text = open.name,
                    style = typography.title,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                open.artist?.let { artist ->
                    Text(
                        text = artist,
                        style = typography.trackArtistCompact,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(spacing.space2)
        ) {
            items(inGroup, key = { it.id }) { item ->
                dev.aurora.player.ui.components.TrackRow(
                    item = item,
                    onPlay = { onPlayFrom(inGroup, inGroup.indexOf(item)) }
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.space2)
    ) {
        items(groups, key = { it.key }) { group ->
            val haptics = LocalHapticEngine.current
            GlassSurface(modifier = Modifier.fillMaxWidth(), level = GlassLevel.Secondary) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.fire(HapticEvent.Tap)
                            onOpen(group.key)
                        }
                        .padding(spacing.space3)
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // The first track's sleeve stands for the group; a stack of four would
                    // claim an arrangement the tags do not support.
                    dev.aurora.player.ui.components.ArtworkSurface(
                        altText = "",
                        artworkUri = tracks.firstOrNull { it.id == group.trackIds.firstOrNull() }
                            ?.artworkUri,
                        modifier = Modifier.size(48.dp).clearAndSetSemantics {}
                    )
                    Column(modifier = Modifier.padding(start = spacing.space3).weight(1f)) {
                        Text(
                            text = group.name,
                            style = typography.trackTitle,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = listOfNotNull(
                                group.artist,
                                "${group.trackIds.size} " +
                                    if (group.trackIds.size == 1) "track" else "tracks"
                            ).joinToString(" · "),
                            style = typography.trackArtistCompact,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** A list with nothing in it, saying which nothing this is. */
@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
) {
    val spacing = Aurora.spacing
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.space8),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Aurora.colors.textSecondary,
            modifier = Modifier.padding(bottom = spacing.space4)
        )
        Text(
            text = title,
            style = Aurora.typography.title,
            color = Aurora.colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(spacing.space2))
        Text(
            text = detail,
            style = Aurora.typography.body,
            color = Aurora.colors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}
