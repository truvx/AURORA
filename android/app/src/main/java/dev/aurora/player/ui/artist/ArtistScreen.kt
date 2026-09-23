package dev.aurora.player.ui.artist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.ui.components.ArtworkSurface
import dev.aurora.player.ui.components.GlassIconButton
import dev.aurora.player.ui.components.PrimaryPlayButton
import dev.aurora.player.ui.haptics.HapticEvent
import dev.aurora.player.ui.haptics.LocalHapticEngine
import dev.aurora.player.ui.navigation.GlassCircleButton
import dev.aurora.player.ui.navigation.LocalChromeInsets
import dev.aurora.player.ui.theme.Aurora

/**
 * One artist: who they are in this library, and what to play by them.
 *
 * There is no biography, no follower count and no chart position, because AURORA has no
 * source for any of them and inventing them is exactly what AGENTS.md rules out. What it
 * does have is real: their tracks, ordered by how often this listener played them.
 */
@Composable
fun ArtistScreen(
    artist: String,
    topSongs: List<MediaItem>,
    isFollowed: Boolean,
    onBack: () -> Unit,
    onFollow: (Boolean) -> Unit,
    onPlayTracks: (List<MediaItem>, Boolean) -> Unit,
    onQueueTracks: (List<MediaItem>) -> Unit,
    onSearchOnline: (String) -> Unit,
) {
    val spacing = Aurora.spacing
    val typography = Aurora.typography
    val colors = Aurora.colors
    val haptics = LocalHapticEngine.current
    LazyColumn(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = spacing.space5,
            end = spacing.space5,
            top = spacing.space4,
            // The chrome floats over the content, so the last row has to clear it.
            bottom = LocalChromeInsets.current.calculateBottomPadding() + spacing.space8,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassCircleButton(
                    icon = Icons.Outlined.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                    size = 44.dp,
                )
            }
        }

        item {
            // Their most-played cover stands in for a portrait. AURORA has no artist
            // images, and a stock silhouette would say less than their own record does.
            ArtworkSurface(
                altText = "Artwork for $artist",
                artworkUri = topSongs.firstOrNull()?.artworkUri,
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .padding(top = spacing.space4),
            )
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = spacing.space5),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = artist,
                    style = typography.display,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                // Only when there is something to count. With nothing by them in the
                // library the empty state below says so once, in a full sentence.
                if (topSongs.isNotEmpty()) {
                    Text(
                        text = songCountLabel(topSongs.size),
                        style = typography.body,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        // Nothing to play, so nothing that says it plays. Three controls that quietly do
        // nothing is worse than an empty state that tells the truth.
        if (topSongs.isNotEmpty()) item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.space5),
                horizontalArrangement = Arrangement.spacedBy(spacing.space4, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassCircleButton(
                    icon = Icons.Outlined.Shuffle,
                    contentDescription = "Shuffle $artist",
                    onClick = { onPlayTracks(topSongs, true) },
                    size = 52.dp,
                )
                PrimaryPlayButton(
                    text = "Play",
                    onClick = { onPlayTracks(topSongs, false) },
                )
                GlassCircleButton(
                    icon = Icons.Outlined.PlaylistAdd,
                    contentDescription = "Add $artist to the queue",
                    onClick = { onQueueTracks(topSongs) },
                    size = 52.dp,
                )
            }
        }

        item {
            /*
             * Following is a local watch, not a social act: it puts this artist in the
             * Home feed's release checks even with nothing played yet. The label says
             * that, rather than implying anyone is notified.
             */
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = spacing.space5, bottom = spacing.space6),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GlassCircleButton(
                    icon = if (isFollowed) Icons.Outlined.Check else Icons.Outlined.PersonAdd,
                    contentDescription = if (isFollowed) {
                        "Following $artist. Tap to stop watching for their releases."
                    } else {
                        "Follow $artist to watch for their releases."
                    },
                    onClick = {
                        haptics.fire(HapticEvent.Selection)
                        onFollow(!isFollowed)
                    },
                    selected = isFollowed,
                    size = 52.dp,
                    modifier = Modifier.semantics {
                        // Announced as a state, not only as a changed icon.
                        stateDescription = if (isFollowed) "Following" else "Not following"
                    },
                )
                Text(
                    text = if (isFollowed) "Watching for new releases" else "Follow for new releases",
                    style = typography.label,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = spacing.space2),
                )
            }
        }

        if (topSongs.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Nothing by $artist in your library yet.",
                        style = typography.body,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    PrimaryPlayButton(
                        text = "Search YouTube",
                        onClick = { onSearchOnline(artist) },
                        modifier = Modifier.padding(top = spacing.space4),
                    )
                }
            }
        } else {
            item {
                Text(
                    text = "TOP SONGS",
                    style = typography.section,
                    color = colors.textSecondary,
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space3),
                )
            }
            itemsIndexed(topSongs, key = { _, track -> track.id }) { index, track ->
                ArtistTrackRow(
                    track = track,
                    index = index,
                    onPlay = { onPlayTracks(topSongs.drop(index), false) },
                    onQueue = { onQueueTracks(listOf(track)) },
                )
            }
        }
    }
}

internal fun songCountLabel(count: Int): String = when (count) {
    0 -> "Not in your library"
    1 -> "1 song in your library"
    else -> "$count songs in your library"
}

/**
 * One song on the artist page: its place in the ranking, then the track.
 *
 * The number is decoration for sighted readers and is hidden from the screen reader - the
 * row already announces its position through the list - while "Play" and "Add to queue"
 * are named after the track so they are distinguishable in a list of controls.
 */
@Composable
private fun ArtistTrackRow(
    track: MediaItem,
    index: Int,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
) {
    val spacing = Aurora.spacing
    val typography = Aurora.typography
    val colors = Aurora.colors

    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${index + 1}",
            style = typography.body,
            color = colors.textSecondary,
            modifier = Modifier
                .width(28.dp)
                .clearAndSetSemantics {},
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onPlay),
        ) {
            Text(
                text = track.title,
                style = typography.trackTitle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        GlassIconButton(
            icon = Icons.Outlined.PlaylistAdd,
            contentDesc = "Add ${track.title} to the queue",
            onClick = onQueue,
        )
    }
}
