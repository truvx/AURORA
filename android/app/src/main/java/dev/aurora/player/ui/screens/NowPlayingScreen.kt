package dev.aurora.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.aurora.player.ui.artwork.rememberArtworkPalette
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.aurora.player.domain.player.*
import dev.aurora.player.ui.components.*
import dev.aurora.player.ui.haptics.HapticEvent
import dev.aurora.player.ui.haptics.LocalHapticEngine
import dev.aurora.player.ui.theme.Aurora

@Composable
fun NowPlayingScreen(
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coordinator = LocalPlayerCoordinator.current
    val haptics = LocalHapticEngine.current
    val state by coordinator.state.collectAsState()
    
    val currentTrack = state.currentTrack

    /*
     * The player carries the artwork's own atmosphere rather than a flat canvas.
     *
     * This is the most artwork-forward screen in the app, and it sits above the shell, so a
     * plain opaque background painted straight over the ambient layer - which is what it had
     * - hid exactly the thing the glass elsewhere exists to show. Still opaque, because the
     * screen behind it is a different task and should not read through.
     */
    val palette by rememberArtworkPalette(currentTrack?.artworkUri)

    AmbientArtworkLayer(
        modifier = modifier.fillMaxSize(),
        artworkColor = palette
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            /*
             * This is drawn over the shell rather than inside the Scaffold, so it gets none
             * of the Scaffold's inset handling and has to ask for its own. Without it the
             * close and queue controls sit underneath the system status bar, where they are
             * visible but unreachable - the status bar takes the touches.
             */
            .systemBarsPadding()
            .padding(Aurora.spacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(
                onClick = {
                    haptics.fire(HapticEvent.Tap)
                    onClose()
                },
                icon = Icons.Rounded.KeyboardArrowDown,
                contentDesc = "Close",
            )
            Text(
                text = "Now Playing",
                style = Aurora.typography.title,
                color = Aurora.colors.textPrimary
            )
            GlassIconButton(
                onClick = {
                    haptics.fire(HapticEvent.Tap)
                    onOpenQueue()
                },
                icon = Icons.Rounded.QueueMusic,
                contentDesc = "Queue",
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Artwork
        ArtworkSurface(
            altText = "${currentTrack?.title ?: "No track"} artwork",
            artworkUri = currentTrack?.artworkUri,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Aurora.spacing.space4),
            // Only YouTube brings its own surface; everything else shows its cover.
            content = if (currentTrack?.provider == dev.aurora.player.domain.models.ProviderKind.YOUTUBE) {
                { YouTubePlayerSurface(modifier = Modifier.fillMaxSize()) }
            } else {
                null
            }
        )

        Spacer(modifier = Modifier.height(Aurora.spacing.space10))

        // Title and Artist
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = currentTrack?.title ?: "No track playing",
                style = Aurora.typography.headline,
                color = Aurora.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = currentTrack?.artist ?: "",
                style = Aurora.typography.title,
                color = Aurora.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(Aurora.spacing.space6))

        // Scrubber
        PlayerScrubber(
            position = state.position,
            onSeek = { coordinator.dispatch(PlayerCommand.Seek(it)) }
        )

        Spacer(modifier = Modifier.height(Aurora.spacing.space6))

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle
            val shuffleMode = state.queue.shuffleMode
            val isShuffleOn = shuffleMode == ShuffleMode.On
            GlassIconButton(
                onClick = {
                    haptics.fire(HapticEvent.Toggle)
                    coordinator.dispatch(PlayerCommand.SetShuffle(if (isShuffleOn) ShuffleMode.Off else ShuffleMode.On))
                },
                icon = Icons.Rounded.Shuffle,
                contentDesc = "Shuffle",
                selected = isShuffleOn,
            )

            // Previous
            GlassIconButton(
                onClick = {
                    haptics.fire(HapticEvent.SkipPrevious)
                    coordinator.dispatch(PlayerCommand.SkipPrevious)
                },
                icon = Icons.Rounded.SkipPrevious,
                contentDesc = "Previous",
                modifier = Modifier.size(64.dp)
            )

            // Play/Pause
            val isPlaying = state.status == PlaybackStatus.Playing || state.status == PlaybackStatus.Buffering
            GlassIconButton(
                onClick = {
                    if (isPlaying) {
                        haptics.fire(HapticEvent.Pause)
                        coordinator.dispatch(PlayerCommand.Pause)
                    } else {
                        haptics.fire(HapticEvent.Resume)
                        coordinator.dispatch(PlayerCommand.Play)
                    }
                },
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDesc = if (isPlaying) "Pause" else "Play",
                selected = isPlaying, // to give it a highlighted tint maybe
                modifier = Modifier.size(80.dp)
            )

            // Next
            GlassIconButton(
                onClick = {
                    haptics.fire(HapticEvent.SkipNext)
                    coordinator.dispatch(PlayerCommand.SkipNext)
                },
                icon = Icons.Rounded.SkipNext,
                contentDesc = "Next",
                modifier = Modifier.size(64.dp)
            )

            // Repeat
            val repeatMode = state.queue.repeatMode
            val repeatIcon = when (repeatMode) {
                RepeatMode.One -> Icons.Rounded.RepeatOne
                else -> Icons.Rounded.Repeat
            }
            val repeatSelected = repeatMode != RepeatMode.Off
            GlassIconButton(
                onClick = {
                    haptics.fire(HapticEvent.Toggle)
                    val nextMode = when (repeatMode) {
                        RepeatMode.Off -> RepeatMode.All
                        RepeatMode.All -> RepeatMode.One
                        RepeatMode.One -> RepeatMode.Off
                    }
                    coordinator.dispatch(PlayerCommand.SetRepeat(nextMode))
                },
                icon = repeatIcon,
                contentDesc = "Repeat",
                selected = repeatSelected,
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
    }
    }
}
