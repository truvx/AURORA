/*
 * The Immersive layout is derived from NomaTune's PlayerDesignStyle.V7
 * (app/src/main/kotlin/com/shahdullah/nomatune/ui/player/Player.kt: V7PlayerBackdrop,
 * V7BackdropPalette, v7BackdropTone; PlayerComponents.kt: V8PlayerControlsContent and the
 * V8 metadata, progress, transport and volume rows), https://github.com/Shahdullah/NomaTune,
 * GPL-3.0. Rewritten against AURORA's PlayerCoordinator; see docs/THIRD_PARTY.md.
 */
package dev.aurora.player.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.aurora.player.app.SleepTimer
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.domain.player.PlaybackStatus
import dev.aurora.player.domain.player.PlayerCommand
import dev.aurora.player.domain.player.RepeatMode
import dev.aurora.player.domain.player.ShuffleMode
import dev.aurora.player.ui.artwork.rememberAlbumArtwork
import dev.aurora.player.ui.artwork.rememberArtworkBitmap
import dev.aurora.player.ui.artwork.rememberArtworkPalette
import dev.aurora.player.ui.components.LocalPlayerCoordinator
import dev.aurora.player.ui.components.PlayerScrubber
import dev.aurora.player.ui.components.rememberMusicVolume
import dev.aurora.player.ui.components.rememberOutputDeviceName
import dev.aurora.player.ui.components.showSystemOutputSwitcher
import dev.aurora.player.ui.haptics.HapticEvent
import dev.aurora.player.ui.haptics.LocalHapticEngine
import dev.aurora.player.ui.lyrics.rememberLyrics
import dev.aurora.player.ui.theme.Aurora
import kotlinx.coroutines.delay

/**
 * Now Playing, as NomaTune's Immersive player.
 *
 * A local file's cover fills the top of the screen and fades into a field of its own
 * darkest colour, where white controls sit. A YouTube track puts the official player in
 * that place instead, full width and with nothing drawn over it - YouTube's Required
 * Minimum Functionality forbids anything in front of the player and asks for at least
 * 200x200px - and the colour field starts below its edge rather than across it.
 */
@Composable
fun NowPlayingScreen(
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenArtist: (String) -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: (Boolean) -> Unit = {},
    sleepTimer: SleepTimer? = null,
) {
    val coordinator = LocalPlayerCoordinator.current
    val state by coordinator.state.collectAsState()
    val currentTrack = state.currentTrack
    val artwork by rememberAlbumArtwork(currentTrack)
    val lyrics by rememberLyrics(
        identity = artwork.identity,
        // The player's own duration where it has one: it is the length of what is actually
        // loaded, which is what a lyrics file has to agree with.
        durationMs = state.position.duration ?: currentTrack?.technicalMetadata?.durationMs,
    )
    var lyricsOpen by remember { mutableStateOf(false) }
    // Closes the lyrics before the player, because it was opened last.
    androidx.activity.compose.BackHandler(enabled = lyricsOpen) { lyricsOpen = false }

    val dominant by rememberArtworkPalette(artwork.artworkUri)
    val top by animateColorAsState(immersiveTone(dominant, 0.20f, 0.72f), tween(COLOUR_FADE_MS), label = "top")
    val floor by animateColorAsState(immersiveTone(dominant, 0.08f, 0.32f), tween(COLOUR_FADE_MS), label = "floor")
    val isYouTube = currentTrack?.provider == ProviderKind.YOUTUBE

    val controls: @Composable (Modifier) -> Unit = { controlsModifier ->
        ImmersiveControls(
            title = artwork.title ?: currentTrack?.title ?: "Nothing is playing",
            artist = artwork.artist?.takeIf { it.isNotBlank() },
            // Local files only: YouTube's quality is the provider's and stays unstated.
            codec = currentTrack?.technicalMetadata
                ?.takeIf { !isYouTube }
                ?.let { codecLabel(it.codec, it.container) },
            isFavorite = isFavorite,
            hasTrack = currentTrack != null,
            sleepTimer = sleepTimer,
            onToggleFavorite = onToggleFavorite,
            onOpenArtist = onOpenArtist,
            onOpenQueue = onOpenQueue,
            onOpenLyrics = { lyricsOpen = true },
            modifier = controlsModifier,
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(floor)) {
        val stageHeight = maxHeight * STAGE_FRACTION
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stageHeight)
                .background(top),
        ) {
            AnimatedContent(
                    targetState = artwork.artworkUri,
                    transitionSpec = { fadeIn(tween(ARTWORK_FADE_MS)) togetherWith fadeOut(tween(ARTWORK_FADE_MS)) },
                    label = "artwork",
                ) { uri ->
                    val bitmap by rememberArtworkBitmap(uri)
                    bitmap?.let {
                        Image(
                            bitmap = it,
                            // Decorative: the title is written out below it.
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clearAndSetSemantics {},
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.40f to Color.Transparent,
                                0.60f to floor.copy(alpha = 0.18f),
                                0.76f to floor.copy(alpha = 0.52f),
                                0.88f to floor.copy(alpha = 0.82f),
                                1f to floor,
                            )
                        ),
                )
                CloseButton(onClose, Modifier.statusBarsPadding())
            }
            controls(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )

            if (lyricsOpen) {
                LyricsSheet(
                    state = lyrics,
                    positionMs = state.position.elapsed,
                    title = artwork.title ?: currentTrack?.title,
                    artist = artwork.artist,
                    onClose = { lyricsOpen = false },
                )
            }
        }
    }

@Composable
private fun CloseButton(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticEngine.current
    RoundButton(
        icon = Icons.Rounded.KeyboardArrowDown,
        contentDescription = "Close",
        onClick = {
            haptics.fire(HapticEvent.Tap)
            onClose()
        },
        modifier = modifier.padding(start = 12.dp, top = 8.dp),
    )
}

@Composable
private fun ImmersiveControls(
    title: String,
    artist: String?,
    codec: String?,
    isFavorite: Boolean,
    hasTrack: Boolean,
    sleepTimer: SleepTimer?,
    onToggleFavorite: (Boolean) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coordinator = LocalPlayerCoordinator.current
    val state by coordinator.state.collectAsState()
    val haptics = LocalHapticEngine.current
    val isPlaying = state.status == PlaybackStatus.Playing || state.status == PlaybackStatus.Buffering

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val sidePadding = if (maxWidth < 380.dp) 22.dp else 24.dp
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = sidePadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = title,
                        style = Aurora.typography.trackTitleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )
                    if (artist != null) {
                        Text(
                            text = artist,
                            style = Aurora.typography.trackArtist,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .basicMarquee()
                                .clickable(onClickLabel = "Open $artist") {
                                    haptics.fire(HapticEvent.Selection)
                                    onOpenArtist(artist)
                                },
                        )
                    }
                }
                if (hasTrack) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        MoreMenu(
                            shuffleOn = state.queue.shuffleMode == ShuffleMode.On,
                            repeatMode = state.queue.repeatMode,
                            artist = artist,
                            onCommand = coordinator::dispatch,
                            onOpenArtist = onOpenArtist,
                        )
                        RoundButton(
                            icon = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove $title from favorites" else "Add $title to favorites",
                            onClick = {
                                haptics.fire(HapticEvent.Toggle)
                                onToggleFavorite(!isFavorite)
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            PlayerScrubber(
                position = state.position,
                onSeek = { coordinator.dispatch(PlayerCommand.Seek(it)) },
                centerContent = codec?.let { { CodecChip(it) } },
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportButton(Icons.Rounded.FastRewind, "Previous", 64.dp, 44.dp) {
                    haptics.fire(HapticEvent.Tap)
                    coordinator.dispatch(PlayerCommand.SkipPrevious)
                }
                TransportButton(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    touch = 72.dp,
                    iconSize = 52.dp,
                ) {
                    haptics.fire(HapticEvent.Toggle)
                    coordinator.dispatch(if (isPlaying) PlayerCommand.Pause else PlayerCommand.Play)
                }
                TransportButton(Icons.Rounded.FastForward, "Next", 64.dp, 44.dp) {
                    haptics.fire(HapticEvent.Tap)
                    coordinator.dispatch(PlayerCommand.SkipNext)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            VolumeRow()

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RoundButton(Icons.AutoMirrored.Rounded.FormatListBulleted, "Queue", onClick = {
                    haptics.fire(HapticEvent.Tap)
                    onOpenQueue()
                })
                if (hasTrack) {
                    RoundButton(Icons.Rounded.Lyrics, "Lyrics", onClick = {
                        haptics.fire(HapticEvent.Tap)
                        onOpenLyrics()
                    })
                }
                if (sleepTimer != null) SleepButton(sleepTimer)
                Spacer(modifier = Modifier.weight(1f))
                OutputChip()
            }
        }
    }
}

/** NomaTune's V7 action button: a 48dp circle of translucent white. */
@Composable
private fun RoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    content: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.16f),
        contentColor = Color.White,
        modifier = modifier.size(48.dp).semantics { this.contentDescription = contentDescription },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (content != null) {
                content()
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
            }
        }
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    contentDescription: String,
    touch: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = Color.White,
        modifier = Modifier.size(touch),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
private fun CodecChip(label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.White.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.13f)),
        contentColor = Color.White.copy(alpha = 0.72f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(15.dp))
            Text(text = label, style = Aurora.typography.caption, maxLines = 1)
        }
    }
}

/**
 * The overflow: what the reference has no room for on the screen itself.
 *
 * Shuffle and repeat live here because the Immersive layout's transport is three buttons.
 */
@Composable
private fun MoreMenu(
    shuffleOn: Boolean,
    repeatMode: RepeatMode,
    artist: String?,
    onCommand: (PlayerCommand) -> Unit,
    onOpenArtist: (String) -> Unit,
) {
    val haptics = LocalHapticEngine.current
    var open by remember { mutableStateOf(false) }
    Box {
        RoundButton(Icons.Rounded.MoreVert, "More options", onClick = {
            haptics.fire(HapticEvent.Tap)
            open = true
        })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(if (shuffleOn) "Shuffle: on" else "Shuffle: off") },
                onClick = {
                    haptics.fire(HapticEvent.Toggle)
                    onCommand(PlayerCommand.SetShuffle(if (shuffleOn) ShuffleMode.Off else ShuffleMode.On))
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        when (repeatMode) {
                            RepeatMode.Off -> "Repeat: off"
                            RepeatMode.All -> "Repeat: all"
                            RepeatMode.One -> "Repeat: this track"
                        }
                    )
                },
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
            )
            if (artist != null) {
                DropdownMenuItem(
                    text = { Text("Go to $artist") },
                    onClick = {
                        open = false
                        onOpenArtist(artist)
                    },
                )
            }
        }
    }
}

@Composable
private fun SleepButton(timer: SleepTimer) {
    val haptics = LocalHapticEngine.current
    val endsAt by timer.endsAt.collectAsState()
    var open by remember { mutableStateOf(false) }
    // Whole minutes left, refreshed often enough for a minute display.
    val minutesLeft by produceState<Long?>(null, endsAt) {
        while (endsAt != null) {
            value = timer.remaining()?.let { (it + 59_999) / 60_000 }
            delay(5_000)
        }
        value = null
    }

    Box {
        RoundButton(
            icon = Icons.Rounded.Bedtime,
            contentDescription = minutesLeft?.let { "Sleep timer, $it minutes left" } ?: "Sleep timer",
            onClick = {
                haptics.fire(HapticEvent.Tap)
                open = true
            },
            content = minutesLeft?.let { left ->
                { Text("${left}m", style = Aurora.typography.caption, color = Color.White) }
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(15, 30, 45, 60).forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(if (minutes == 60) "1 hour" else "$minutes minutes") },
                    onClick = {
                        haptics.fire(HapticEvent.Commit)
                        timer.start(minutes)
                        open = false
                    },
                )
            }
            if (endsAt != null) {
                DropdownMenuItem(
                    text = { Text("Turn off") },
                    onClick = {
                        timer.cancel()
                        open = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeRow() {
    val volume = rememberMusicVolume()
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.AutoMirrored.Rounded.VolumeOff,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.72f),
            modifier = Modifier.size(22.dp),
        )
        Slider(
            value = volume.fraction,
            onValueChange = volume.set,
            thumb = {},
            track = { sliderState ->
                val range = sliderState.valueRange
                val fraction = ((sliderState.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
                    val radius = CornerRadius(size.height / 2f)
                    drawRoundRect(Color.White.copy(alpha = 0.24f), size = size, cornerRadius = radius)
                    drawRoundRect(
                        Color.White.copy(alpha = 0.86f),
                        topLeft = Offset.Zero,
                        size = Size(size.width * fraction, size.height),
                        cornerRadius = radius,
                    )
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 18.dp)
                .semantics { contentDescription = "Volume" },
        )
        Icon(
            Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.72f),
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Where the sound is going. Opens the system's output picker where Android has one. */
@Composable
private fun OutputChip() {
    val context = LocalContext.current
    val device = rememberOutputDeviceName()
    Surface(
        onClick = { showSystemOutputSwitcher(context) },
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.16f),
        contentColor = Color.White,
        modifier = Modifier.height(48.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 18.dp),
        ) {
            Icon(
                if (device != null) Icons.Rounded.Bluetooth else Icons.Rounded.PhoneAndroid,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = device ?: "This phone",
                style = Aurora.typography.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = MAX_CHIP_TEXT),
            )
        }
    }
}

/**
 * NomaTune's v7BackdropTone: the cover's dominant hue, saturation lifted a little and
 * brightness clamped, so white text always reads on it.
 */
private fun immersiveTone(dominant: Color?, valueMin: Float, valueMax: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV((dominant ?: Color.Black).toArgb(), hsv)
    hsv[1] = if (hsv[1] < 0.12f) hsv[1].coerceAtMost(0.08f) else (hsv[1] * 1.22f).coerceIn(0f, 1f)
    hsv[2] = if (dominant == null) 0.12f else hsv[2].coerceIn(valueMin, valueMax)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * The codec's short name, from the MIME types the scanner stores - NomaTune's codecLabel,
 * extended for MP3 and PCM. Raw PCM is named by its container, since that is what the
 * listener knows it as. Null when nothing was measured: unknown stays unknown.
 */
internal fun codecLabel(codec: String?, container: String?): String? {
    val raw = codec?.substringAfter("/")?.substringBefore(";")?.trim()?.uppercase()
    if (raw.isNullOrEmpty()) return null
    return when {
        "FLAC" in raw -> "FLAC"
        "ALAC" in raw -> "ALAC"
        "OPUS" in raw -> "OPUS"
        "VORBIS" in raw -> "VORBIS"
        "MP4A" in raw || "AAC" in raw -> "AAC"
        raw == "MPEG" || "MP3" in raw -> "MP3"
        raw == "RAW" -> if (container?.contains("wav", ignoreCase = true) == true) "WAV" else "PCM"
        else -> raw
    }
}

/** NomaTune's portrait sharp-stage height. */
private const val STAGE_FRACTION = 0.62f
private const val ARTWORK_FADE_MS = 900
private const val COLOUR_FADE_MS = 600
private val MAX_CHIP_TEXT = 140.dp
