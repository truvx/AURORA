package dev.aurora.player.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.domain.player.PlaybackStatus
import dev.aurora.player.domain.player.PlayerCommand
import dev.aurora.player.domain.player.RepeatMode
import dev.aurora.player.domain.player.ShuffleMode
import dev.aurora.player.ui.artwork.rememberArtworkBitmap
import dev.aurora.player.ui.artwork.rememberArtworkPalette
import dev.aurora.player.ui.components.AmbientArtworkLayer
import dev.aurora.player.ui.components.GlassIconButton
import dev.aurora.player.ui.components.GlassLevel
import dev.aurora.player.ui.components.GlassSurface
import dev.aurora.player.ui.components.LocalPlayerCoordinator
import dev.aurora.player.ui.components.PlayerScrubber
import dev.aurora.player.ui.components.PrimaryCircleButton
import dev.aurora.player.ui.components.YouTubePlayerSurface
import dev.aurora.player.ui.haptics.HapticEvent
import dev.aurora.player.ui.haptics.LocalHapticEngine
import dev.aurora.player.ui.theme.Aurora

/**
 * Now Playing: the record fills the screen, and the controls float over it.
 *
 * Built from the reference in `ui demo/music palyer ui.png`. The artwork is the screen
 * rather than a square in the middle of one, and the words and the transport sit on two
 * separate panes of glass over it, low enough to reach with a thumb.
 *
 * YouTube is the exception and has to be: its player must stay visible and unobstructed,
 * so a YouTube track shows the official surface at the top of the screen with the glass
 * below it rather than across it. That is a policy requirement, not a layout preference.
 */
@Composable
fun NowPlayingScreen(
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenArtist: (String) -> Unit = {},
) {
    val coordinator = LocalPlayerCoordinator.current
    val haptics = LocalHapticEngine.current
    val state by coordinator.state.collectAsState()
    val colors = Aurora.colors
    val spacing = Aurora.spacing

    val currentTrack = state.currentTrack
    val palette by rememberArtworkPalette(currentTrack?.artworkUri)
    val isYouTube = currentTrack?.provider == ProviderKind.YOUTUBE

    AmbientArtworkLayer(modifier = modifier.fillMaxSize(), artworkColor = palette) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!isYouTube) {
                FullBleedArtwork(artworkUri = currentTrack?.artworkUri)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    /*
                     * Drawn over the shell rather than inside a Scaffold, so it gets none
                     * of the Scaffold's inset handling and has to ask for its own. Without
                     * it the close and queue controls sit under the status bar, where they
                     * are visible but unreachable - the status bar takes the touches.
                     */
                    .systemBarsPadding()
                    .padding(horizontal = spacing.space4)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.space2),
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
                    GlassIconButton(
                        onClick = {
                            haptics.fire(HapticEvent.Tap)
                            onOpenQueue()
                        },
                        icon = Icons.Rounded.QueueMusic,
                        contentDesc = "Queue",
                    )
                }

                if (isYouTube) {
                    Spacer(modifier = Modifier.height(spacing.space4))
                    // Visible, unobstructed, and the right shape for video. Nothing is
                    // drawn over it.
                    YouTubePlayerSurface(
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    )
                }

                // The glass sits at the bottom, where a thumb is.
                Spacer(modifier = Modifier.weight(1f))

                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    level = GlassLevel.Elevated,
                    shape = Aurora.shapes.card
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(spacing.space4)) {
                        Text(
                            text = currentTrack?.title ?: "Nothing is playing",
                            style = Aurora.typography.headline,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        val artistName = currentTrack?.artist?.takeIf { it.isNotBlank() }
                        if (artistName != null) {
                            Text(
                                text = artistName,
                                style = Aurora.typography.title,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    // The one thing on this screen that is about someone
                                    // rather than about this track.
                                    .clickable(onClickLabel = "Open $artistName") {
                                        haptics.fire(HapticEvent.Selection)
                                        onOpenArtist(artistName)
                                    }
                                    .padding(vertical = spacing.space1)
                            )
                        }

                        Spacer(modifier = Modifier.height(spacing.space3))

                        PlayerScrubber(
                            position = state.position,
                            onSeek = { coordinator.dispatch(PlayerCommand.Seek(it)) }
                        )

                        val (left, right) = trackFacts(currentTrack)
                        if (left != null || right != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = spacing.space2),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = left.orEmpty(),
                                    style = Aurora.typography.label,
                                    color = colors.textTertiary
                                )
                                Text(
                                    text = right.orEmpty(),
                                    style = Aurora.typography.label,
                                    color = colors.textTertiary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.space3))

                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    level = GlassLevel.Elevated,
                    shape = Aurora.shapes.card
                ) {
                    TransportRow(
                        isPlaying = state.status == PlaybackStatus.Playing ||
                            state.status == PlaybackStatus.Buffering,
                        shuffleOn = state.queue.shuffleMode == ShuffleMode.On,
                        repeatMode = state.queue.repeatMode,
                        onCommand = coordinator::dispatch,
                        modifier = Modifier.fillMaxWidth().padding(spacing.space3)
                    )
                }

                Spacer(modifier = Modifier.height(spacing.space5))
            }
        }
    }
}

/**
 * The record, filling the screen behind everything.
 *
 * Cropped rather than fitted: letterboxing a cover inside a phone-shaped screen leaves two
 * grey bars and turns the most striking thing in the app into a postage stamp. A gradient
 * darkens the lower half so the glass below always has something legible behind it,
 * whatever the artwork is - a pale cover would otherwise leave white text on white.
 */
@Composable
private fun FullBleedArtwork(artworkUri: String?) {
    val artwork by rememberArtworkBitmap(artworkUri)
    val bitmap = artwork ?: return

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            bitmap = bitmap,
            // Decorative: the title is written out immediately below it.
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clearAndSetSemantics {}
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Black.copy(alpha = 0.25f),
                        1f to Color.Black.copy(alpha = 0.72f)
                    )
                )
        )
    }
}

/**
 * The two small facts under the scrubber, as in the reference - but only ones AURORA can
 * actually stand behind.
 *
 * The reference reads "studio album · 2018" and "19 songs · 52m", which comes from a
 * catalogue this app does not have. What it does have is the file's own metadata, so a
 * local track says what it is and how good it is. A YouTube track says "YouTube", because
 * the capability matrix makes its quality provider-determined and unknown, and a bitrate
 * printed here would be invented.
 */
internal fun trackFacts(track: MediaItem?): Pair<String?, String?> {
    if (track == null) return null to null
    if (track.provider == ProviderKind.YOUTUBE) return "YouTube" to track.album

    val metadata = track.technicalMetadata
    val left = listOfNotNull(
        metadata?.container?.substringAfterLast('/')?.uppercase(),
        metadata?.bitrate?.let { "${it / 1000} kbps" },
        metadata?.sampleRate?.let { "${"%.1f".format(java.util.Locale.ROOT, it / 1000f)} kHz" }
    ).take(2).joinToString(" · ").takeIf { it.isNotEmpty() }

    val right = listOfNotNull(
        track.album?.takeIf { it.isNotBlank() },
        if (metadata?.isLossless == true) "Lossless" else null
    ).joinToString(" · ").takeIf { it.isNotEmpty() }

    return left to right
}

@Composable
private fun TransportRow(
    isPlaying: Boolean,
    shuffleOn: Boolean,
    repeatMode: RepeatMode,
    onCommand: (PlayerCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticEngine.current

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIconButton(
            onClick = {
                haptics.fire(HapticEvent.Toggle)
                onCommand(PlayerCommand.SetShuffle(if (shuffleOn) ShuffleMode.Off else ShuffleMode.On))
            },
            icon = Icons.Rounded.Shuffle,
            contentDesc = "Shuffle",
            selected = shuffleOn,
        )

        GlassIconButton(
            onClick = {
                haptics.fire(HapticEvent.SkipPrevious)
                onCommand(PlayerCommand.SkipPrevious)
            },
            icon = Icons.Rounded.SkipPrevious,
            contentDesc = "Previous",
            modifier = Modifier.size(56.dp)
        )

        // The one filled control on the screen, and where the thumb lands.
        PrimaryCircleButton(
            onClick = {
                if (isPlaying) {
                    haptics.fire(HapticEvent.Pause)
                    onCommand(PlayerCommand.Pause)
                } else {
                    haptics.fire(HapticEvent.Resume)
                    onCommand(PlayerCommand.Play)
                }
            },
            icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            size = 72.dp
        )

        GlassIconButton(
            onClick = {
                haptics.fire(HapticEvent.SkipNext)
                onCommand(PlayerCommand.SkipNext)
            },
            icon = Icons.Rounded.SkipNext,
            contentDesc = "Next",
            modifier = Modifier.size(56.dp)
        )

        GlassIconButton(
            onClick = {
                haptics.fire(HapticEvent.Toggle)
                onCommand(
                    PlayerCommand.SetRepeat(
                        when (repeatMode) {
                            RepeatMode.Off -> RepeatMode.All
                            RepeatMode.All -> RepeatMode.One
                            RepeatMode.One -> RepeatMode.Off
                        }
                    )
                )
            },
            icon = if (repeatMode == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            contentDesc = "Repeat",
            selected = repeatMode != RepeatMode.Off,
        )
    }
}
