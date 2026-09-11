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
    modifier: Modifier = Modifier
) {
    if (openPlaylist != null) {
        PlaylistDetail(
            playlist = openPlaylist,
            tracks = openPlaylistTracks,
            onBack = { onOpenPlaylist(null) },
            onRemoveEntry = { entryId -> onRemoveEntry(openPlaylist.id, entryId) },
            onMoveEntry = { from, to -> onMoveEntry(openPlaylist.id, from, to) },
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
                    haptics.fire(HapticEvent.Success)
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
                                    haptics.fire(HapticEvent.Warning)
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
    modifier: Modifier = Modifier
) {
    val spacing = Aurora.spacing
    val typography = Aurora.typography
    val colors = Aurora.colors
    val haptics = LocalHapticEngine.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space3),
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
            Spacer(modifier = Modifier.width(spacing.space3))
            Column(modifier = Modifier.weight(1f)) {
                Text(playlist.name, style = typography.title, color = colors.textPrimary)
                Text(
                    text = trackCountLabel(tracks.size),
                    style = typography.label,
                    color = colors.textSecondary
                )
            }
        }

        if (tracks.isEmpty()) {
            Text(
                text = "This playlist is empty. Add tracks from your library.",
                style = typography.body,
                color = colors.textSecondary,
                modifier = Modifier.padding(vertical = spacing.space6)
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                itemsIndexed(tracks, key = { _, t -> t.entryId }) { index, entry ->
                    GlassSurface(modifier = Modifier.fillMaxWidth(), level = GlassLevel.Secondary) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(spacing.space3),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    // A track whose file is gone still holds its place rather
                                    // than vanishing from the user's playlist.
                                    text = entry.item?.title ?: "Unavailable track",
                                    style = typography.title,
                                    color = if (entry.item != null) {
                                        colors.textPrimary
                                    } else {
                                        colors.textSecondary
                                    }
                                )
                                Text(
                                    // Missing artist metadata is not the same as a missing
                                    // file; saying "unavailable" for an untagged track would
                                    // claim something untrue about the user's library.
                                    text = when {
                                        entry.item == null -> "Not currently available"
                                        entry.item.artist != null -> entry.item.artist!!
                                        else -> "Unknown artist"
                                    },
                                    style = typography.label,
                                    color = colors.textSecondary
                                )
                            }
                            GlassIconButton(
                                icon = Icons.Outlined.ArrowUpward,
                                contentDesc = "Move up",
                                enabled = index > 0,
                                onClick = {
                                    haptics.fire(HapticEvent.QueueReorder)
                                    onMoveEntry(index, index - 1)
                                }
                            )
                            GlassIconButton(
                                icon = Icons.Outlined.ArrowDownward,
                                contentDesc = "Move down",
                                enabled = index < tracks.lastIndex,
                                onClick = {
                                    haptics.fire(HapticEvent.QueueReorder)
                                    onMoveEntry(index, index + 1)
                                }
                            )
                            GlassIconButton(
                                icon = Icons.Outlined.Delete,
                                contentDesc = "Remove from playlist",
                                onClick = {
                                    haptics.fire(HapticEvent.Warning)
                                    onRemoveEntry(entry.entryId)
                                }
                            )
                        }
                    }
                }
            }
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
                                haptics.fire(HapticEvent.Success)
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
