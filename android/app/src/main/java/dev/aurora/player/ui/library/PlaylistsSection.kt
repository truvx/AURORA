package dev.aurora.player.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.aurora.player.domain.library.Playlist
import dev.aurora.player.domain.library.PlaylistTrack
import dev.aurora.player.ui.components.GlassButton
import dev.aurora.player.ui.components.GlassIconButton
import dev.aurora.player.ui.components.GlassLevel
import dev.aurora.player.ui.components.GlassSurface
import dev.aurora.player.ui.haptics.HapticEvent
import dev.aurora.player.ui.haptics.LocalHapticEngine
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import dev.aurora.player.ui.theme.Aurora

/**
 * Playlist browsing and editing. Shows the list of playlists, or the contents of one when
 * [openPlaylist] is set.
 *
 * Reordering uses explicit up/down controls rather than drag: they are reachable by screen
 * reader and keyboard, and each press maps to one transactional move in the repository.
 */
@Composable
fun PlaylistsSection(
    playlists: List<Playlist>,
    openPlaylist: Playlist?,
    openPlaylistTracks: List<PlaylistTrack>,
    onOpenPlaylist: (String?) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onRemoveEntry: (playlistId: String, entryId: String) -> Unit,
    onMoveEntry: (playlistId: String, from: Int, to: Int) -> Unit,
    onPlayTracks: (tracks: List<dev.aurora.player.domain.models.MediaItem>, shuffled: Boolean) -> Unit = { _, _ -> },
    onQueueTracks: (tracks: List<dev.aurora.player.domain.models.MediaItem>) -> Unit = { },
    modifier: Modifier = Modifier
) {
    if (openPlaylist != null) {
        PlaylistDetail(
            playlist = openPlaylist,
            tracks = openPlaylistTracks,
            onBack = { onOpenPlaylist(null) },
            onRemoveEntry = { entryId -> onRemoveEntry(openPlaylist.id, entryId) },
            onMoveEntry = { from, to -> onMoveEntry(openPlaylist.id, from, to) },
            onPlayTracks = onPlayTracks,
            onQueueTracks = onQueueTracks,
            modifier = modifier
        )
    } else {
        PlaylistList(
            playlists = playlists,
            onOpenPlaylist = onOpenPlaylist,
            onCreatePlaylist = onCreatePlaylist,
            onDeletePlaylist = onDeletePlaylist,
            modifier = modifier
        )
    }
}

@Composable
private fun PlaylistList(
    playlists: List<Playlist>,
    onOpenPlaylist: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = Aurora.spacing
    val typography = Aurora.typography
    val colors = Aurora.colors
    val haptics = LocalHapticEngine.current

    var newName by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space3),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                singleLine = true,
                modifier = Modifier.weight(1f),
                placeholder = { Text("New playlist name", style = typography.body) }
            )
            Spacer(modifier = Modifier.width(spacing.space2))
            GlassButton(
                text = "Create",
                enabled = newName.isNotBlank(),
                onClick = {
                    haptics.fire(HapticEvent.Commit)
                    onCreatePlaylist(newName)
                    newName = ""
                }
            )
        }

        if (playlists.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.space8),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.QueueMusic,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.padding(bottom = spacing.space3)
                )
                Text(
                    text = "No playlists yet",
                    style = typography.title,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(spacing.space2))
                Text(
                    text = "Name one above to start collecting tracks.",
                    style = typography.body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                items(playlists, key = { it.id }) { playlist ->
                    GlassSurface(modifier = Modifier.fillMaxWidth(), level = GlassLevel.Secondary) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptics.fire(HapticEvent.Selection)
                                    onOpenPlaylist(playlist.id)
                                }
                                .padding(spacing.space3),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.QueueMusic,
                                contentDescription = null,
                                tint = colors.accentPrimary
                            )
                            Column(
                                modifier = Modifier
                                    .padding(start = spacing.space3)
                                    .weight(1f)
                            ) {
                                Text(
                                    text = playlist.name,
                                    style = typography.title,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = trackCountLabel(playlist.trackCount),
                                    style = typography.label,
                                    color = colors.textSecondary
                                )
                            }
                            GlassIconButton(
                                icon = Icons.Outlined.Delete,
                                contentDesc = "Delete playlist ${playlist.name}",
                                onClick = {
                                    haptics.fire(HapticEvent.Refused)
                                    onDeletePlaylist(playlist.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetail(
    playlist: Playlist,
    tracks: List<PlaylistTrack>,
    onBack: () -> Unit,
    onRemoveEntry: (String) -> Unit,
    onMoveEntry: (Int, Int) -> Unit,
    onPlayTracks: (List<dev.aurora.player.domain.models.MediaItem>, Boolean) -> Unit,
    onQueueTracks: (List<dev.aurora.player.domain.models.MediaItem>) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = Aurora.spacing
    val typography = Aurora.typography
    val colors = Aurora.colors
    val haptics = LocalHapticEngine.current

    val playable = tracks.mapNotNull { it.item }
    // The playlist's own cover: the first track that has artwork. A playlist has no image
    // of its own, and one of its covers is truer than a generated pattern.
    val heroArtwork = tracks.firstNotNullOfOrNull { it.item?.artworkUri }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space4),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(
                    icon = Icons.Outlined.ArrowBack,
                    contentDesc = "Back to all playlists",
                    onClick = {
                        haptics.fire(HapticEvent.Selection)
                        onBack()
                    }
                )
            }
        }

        // --- hero -----------------------------------------------------------------------
        item {
            dev.aurora.player.ui.components.ArtworkSurface(
                altText = "${playlist.name} artwork",
                artworkUri = heroArtwork,
                modifier = Modifier.fillMaxWidth(0.62f)
            )
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = spacing.space5),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = playlist.name,
                    style = typography.display,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = trackCountLabel(tracks.size),
                    style = typography.body,
                    color = colors.textSecondary
                )
            }
        }

        // --- actions --------------------------------------------------------------------
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.space5),
                horizontalArrangement = Arrangement.spacedBy(spacing.space4, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                dev.aurora.player.ui.navigation.GlassCircleButton(
                    icon = Icons.Outlined.Shuffle,
                    contentDescription = "Shuffle ${playlist.name}",
                    onClick = { onPlayTracks(playable, true) },
                    size = 52.dp
                )
                dev.aurora.player.ui.components.PrimaryPlayButton(
                    text = "Play",
                    onClick = { onPlayTracks(playable, false) }
                )
                dev.aurora.player.ui.navigation.GlassCircleButton(
                    icon = Icons.Outlined.PlaylistAdd,
                    contentDescription = "Add ${playlist.name} to the queue",
                    // Queues behind what is playing; it does not interrupt it.
                    onClick = { onQueueTracks(playable) },
                    size = 52.dp
                )
            }
        }

        // --- tracks ---------------------------------------------------------------------
        if (tracks.isEmpty()) {
            item {
                Text(
                    text = "This playlist is empty. Add tracks from your library.",
                    style = typography.body,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(vertical = spacing.space6)
                )
            }
        } else {
            itemsIndexed(tracks, key = { _, t -> t.entryId }) { index, entry ->
                PlaylistTrackRow(
                    entry = entry,
                    index = index,
                    isLast = index == tracks.lastIndex,
                    onPlay = { entry.item?.let { onPlayTracks(listOf(it), false) } },
                    onMoveUp = { onMoveEntry(index, index - 1) },
                    onMoveDown = { onMoveEntry(index, index + 1) },
                    onRemove = { onRemoveEntry(entry.entryId) }
                )
            }
        }
    }
}

/**
 * One row of a playlist, with a hairline under it.
 *
 * Reordering and removal live in the overflow menu rather than as three permanent buttons:
 * the references keep a row to its content and one "more", and the rows were previously
 * wider in controls than in music.
 */
@Composable
private fun PlaylistTrackRow(
    entry: PlaylistTrack,
    index: Int,
    isLast: Boolean,
    onPlay: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val spacing = Aurora.spacing
    val typography = Aurora.typography
    val colors = Aurora.colors
    val haptics = LocalHapticEngine.current
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dev.aurora.player.ui.components.ArtworkSurface(
                altText = "",
                artworkUri = entry.item?.artworkUri,
                modifier = Modifier.size(48.dp).clearAndSetSemantics {}
            )
            Spacer(modifier = Modifier.width(spacing.space3))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = entry.item != null, onClick = onPlay)
            ) {
                Text(
                    // A track whose file is gone still holds its place rather than vanishing
                    // from the user's playlist.
                    text = entry.item?.title ?: "Unavailable track",
                    style = typography.trackTitle,
                    color = if (entry.item != null) colors.textPrimary else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    // Missing artist metadata is not the same as a missing file; saying
                    // "unavailable" for an untagged track would claim something untrue.
                    text = when {
                        entry.item == null -> "Not currently available"
                        entry.item.artist != null -> entry.item.artist!!
                        else -> "Unknown artist"
                    },
                    style = typography.body,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                GlassIconButton(
                    icon = Icons.Outlined.MoreHoriz,
                    contentDesc = "More options for ${entry.item?.title ?: "this track"}",
                    onClick = { menuOpen = true }
                )
                androidx.compose.material3.DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Move up") },
                        enabled = index > 0,
                        onClick = {
                            menuOpen = false
                            haptics.fire(HapticEvent.Commit)
                            onMoveUp()
                        }
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Move down") },
                        enabled = !isLast,
                        onClick = {
                            menuOpen = false
                            haptics.fire(HapticEvent.Commit)
                            onMoveDown()
                        }
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Remove from playlist") },
                        onClick = {
                            menuOpen = false
                            haptics.fire(HapticEvent.Refused)
                            onRemove()
                        }
                    )
                }
            }
        }
        if (!isLast) {
            androidx.compose.material3.HorizontalDivider(
                color = colors.dividerSubtle,
                modifier = Modifier.padding(start = 48.dp + spacing.space3)
            )
        }
    }
}

/**
 * A picker shown when adding a library track to a playlist. Returns null selection when
 * dismissed.
 */
@Composable
fun AddToPlaylistRow(
    playlists: List<Playlist>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = Aurora.spacing
    val typography = Aurora.typography
    val colors = Aurora.colors
    val haptics = LocalHapticEngine.current

    GlassSurface(modifier = modifier.fillMaxWidth(), level = GlassLevel.Primary) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.space3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Add to playlist",
                    style = typography.title,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                GlassIconButton(
                    icon = Icons.Outlined.ArrowBack,
                    contentDesc = "Cancel adding to playlist",
                    onClick = onDismiss
                )
            }
            Spacer(modifier = Modifier.height(spacing.space2))
            if (playlists.isEmpty()) {
                Text(
                    text = "Create a playlist first.",
                    style = typography.body,
                    color = colors.textSecondary
                )
            } else {
                playlists.forEach { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptics.fire(HapticEvent.Commit)
                                onPick(playlist.id)
                            }
                            .padding(vertical = spacing.space2),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            tint = colors.accentPrimary
                        )
                        Spacer(modifier = Modifier.width(spacing.space3))
                        Text(playlist.name, style = typography.body, color = colors.textPrimary)
                    }
                }
            }
        }
    }
}

private fun trackCountLabel(count: Int): String =
    if (count == 1) "1 track" else "$count tracks"
