package dev.aurora.player.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.domain.player.PlaybackStatus
import dev.aurora.player.domain.player.PlayerCommand
import dev.aurora.player.domain.player.RepeatMode
import dev.aurora.player.domain.player.ShuffleMode
import dev.aurora.player.ui.accessibility.LocalAccessibilityPreferences
import dev.aurora.player.ui.artwork.rememberAlbumArtwork
import dev.aurora.player.ui.lyrics.rememberLyrics
import dev.aurora.player.ui.artwork.rememberArtworkBitmap
import dev.aurora.player.ui.artwork.rememberArtworkPalette
import dev.aurora.player.ui.components.AmbientArtworkLayer
import dev.aurora.player.ui.components.GlassIconButton
import dev.aurora.player.ui.components.LocalPlayerCoordinator
import dev.aurora.player.ui.components.PlayerScrubber
import dev.aurora.player.ui.components.PrimaryCircleButton
import dev.aurora.player.ui.components.YouTubePlayerSurface
import dev.aurora.player.ui.haptics.HapticEvent
import dev.aurora.player.ui.haptics.LocalHapticEngine
import dev.aurora.player.ui.theme.Aurora

/**
 * Now Playing: the record, then its name, then the controls.
 *
 * The artwork is a square, centred, at the size a sleeve wants to be. It used to fill the
 * screen, which looked right for a square cover and wrong for everything else - a YouTube
 * thumbnail is a 16:9 still, and cropping one to a tall phone left a face sliced in half
 * behind the glass.
 *
 * YouTube's player takes that same square when a YouTube track is playing. It stays
 * visible and unobstructed, which the capability matrix requires, and it is in the place
 * the eye already goes rather than floating above the artwork it was covering.
 */
@Composable
fun NowPlayingScreen(
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenArtist: (String) -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: (Boolean) -> Unit = {},
) {
    val coordinator = LocalPlayerCoordinator.current
    val haptics = LocalHapticEngine.current
    val state by coordinator.state.collectAsState()
    val colors = Aurora.colors
    val spacing = Aurora.spacing

    val currentTrack = state.currentTrack
    val artwork by rememberAlbumArtwork(currentTrack)
    val lyrics by rememberLyrics(
        identity = artwork.identity,
        // The player's own duration where it has one: it is the length of what is actually
        // loaded, which is what a lyrics file has to agree with.
        durationMs = state.position.duration ?: currentTrack?.technicalMetadata?.durationMs,
    )
    var lyricsOpen by remember { mutableStateOf(false) }
    // Closes the lyrics before the player, because it was opened last. Composed inside the
    // player's own handler, so it is the one that wins.
    androidx.activity.compose.BackHandler(enabled = lyricsOpen) { lyricsOpen = false }
    val palette by rememberArtworkPalette(artwork.artworkUri)
    val isYouTube = currentTrack?.provider == ProviderKind.YOUTUBE
    val isPlaying = state.status == PlaybackStatus.Playing ||
        state.status == PlaybackStatus.Buffering

    // Flipped after the first composition so the entry actually animates from nothing
    // rather than starting already settled.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    AmbientArtworkLayer(modifier = modifier.fillMaxSize(), artworkColor = palette) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                /*
                 * Drawn over the shell rather than inside a Scaffold, so it gets none of
                 * the Scaffold's inset handling and has to ask for its own. Without it the
                 * close and queue controls sit under the status bar, where they are
                 * visible but unreachable - the status bar takes the touches.
                 */
                .systemBarsPadding()
                .padding(horizontal = spacing.space5),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = spacing.space2),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
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
                    style = Aurora.typography.label,
                    color = colors.textPrimary,
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

            AlbumArt(
                artworkUri = artwork.artworkUri,
                title = artwork.title ?: currentTrack?.title,
                modifier = Modifier.staggeredEntry(index = 0, visible = entered),
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredEntry(index = 1, visible = entered),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artwork.title ?: currentTrack?.title ?: "Nothing is playing",
                        style = Aurora.typography.trackTitleLarge,
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val artistName = artwork.artist?.takeIf { it.isNotBlank() }
                    if (artistName != null) {
                        Text(
                            text = artistName.uppercase(),
                            style = Aurora.typography.section,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                // The one thing here that is about someone rather than
                                // about this track.
                                .clickable(onClickLabel = "Open $artistName") {
                                    haptics.fire(HapticEvent.Selection)
                                    onOpenArtist(artistName)
                                }
                                .padding(vertical = spacing.space1),
                        )
                    }
                }

                if (currentTrack != null) {
                    FavoriteButton(
                        isFavorite = isFavorite,
                        title = artwork.title ?: currentTrack.title,
                        onToggle = onToggleFavorite,
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.space4))

            PlayerScrubber(
                position = state.position,
                onSeek = { coordinator.dispatch(PlayerCommand.Seek(it)) },
                modifier = Modifier.staggeredEntry(index = 2, visible = entered),
            )

            Spacer(modifier = Modifier.height(spacing.space5))

            TransportRow(
                modifier = Modifier.staggeredEntry(index = 3, visible = entered),
                isPlaying = isPlaying,
                shuffleOn = state.queue.shuffleMode == ShuffleMode.On,
                repeatMode = state.queue.repeatMode,
                onCommand = coordinator::dispatch,
            )

            if (currentTrack != null) {
                Spacer(modifier = Modifier.height(spacing.space4))
                LyricsPill(
                    onClick = {
                        haptics.fire(HapticEvent.Tap)
                        lyricsOpen = true
                    },
                    modifier = Modifier.staggeredEntry(index = 4, visible = entered),
                )
            }

            if (isYouTube) {
                Spacer(modifier = Modifier.height(spacing.space4))
                YouTubePlayerCard(
                    modifier = Modifier.staggeredEntry(index = 5, visible = entered),
                )
            }

            Spacer(modifier = Modifier.height(spacing.space5))
        }

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

/**
 * The way in to the words, from the reference.
 *
 * A pill rather than an icon because it is the only control on this screen whose meaning is
 * not obvious from a glyph, and because the reference puts a named action here - the eye
 * finishes on the transport and then finds one more thing to do.
 */
@Composable
private fun LyricsPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    dev.aurora.player.ui.components.GlassButton(
        text = "Lyrics",
        onClick = onClick,
        // Secondary, not the white primary pill: the play button is the one filled control
        // on this screen, and a second one would argue with it.
        style = dev.aurora.player.ui.components.GlassButtonStyle.Secondary,
        modifier = modifier,
    )
}

/**
 * Entry, one element after another.
 *
 * Everything used to arrive in the same frame, which is the difference between a screen
 * that appears and a screen that assembles. Staggering by a few frames builds the
 * hierarchy the eye should follow - the record, then what it is, then the controls - and
 * costs nothing but a delay per element.
 *
 * Reduced motion gets all of it at once, because this is movement for its own sake and
 * that is exactly what the setting asks to remove.
 */
@Composable
private fun Modifier.staggeredEntry(index: Int, visible: Boolean): Modifier {
    val reducedMotion = LocalAccessibilityPreferences.current.reducedMotion
    if (reducedMotion) return this

    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = Aurora.motion.durationStandard,
            delayMillis = index * STAGGER_MS,
            easing = Aurora.motion.easingDecelerate,
        ),
        label = "entry $index",
    )
    val lift = with(LocalDensity.current) { ENTRY_LIFT.toPx() }

    return graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * lift
    }
}

/** Roughly two frames at 60Hz - enough to read as a sequence, not as a queue. */
private const val STAGGER_MS = 45
private val ENTRY_LIFT = 20.dp

/**
 * The sleeve, square and centred.
 *
 * Always the record's own cover, including for YouTube: the video's frame is not a sleeve,
 * and a still of someone's face where the artwork should be is the wrong picture rather
 * than a smaller one. What is playing from YouTube keeps its own player, further down.
 *
 * A new track crossfades in rather than cutting: the covers are the one thing on this
 * screen that changes wholesale, and a hard swap between two photographs reads as a glitch.
 */
@Composable
private fun AlbumArt(
    artworkUri: String?,
    title: String?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .fillMaxWidth(0.72f)
            .aspectRatio(1f)
            .shadow(elevation = 32.dp, shape = shape, clip = false)
            .clip(shape)
            .background(Aurora.colors.surfaceGlassSecondary),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = artworkUri,
            transitionSpec = {
                (fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 0.96f))
                    .togetherWith(fadeOut(tween(200)))
            },
            label = "album art",
        ) { uri ->
            val bitmap by rememberArtworkBitmap(uri)
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image,
                    // Decorative: the title is written out directly below it.
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clearAndSetSemantics {},
                )
            } else {
                // A record with no sleeve is drawn as one, not left as a hole.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Aurora.colors.surfaceGlassPrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title?.take(1)?.uppercase().orEmpty(),
                        style = Aurora.typography.display,
                        color = Aurora.colors.textTertiary,
                    )
                }
            }
        }
    }
}

/**
 * The official YouTube player, where a YouTube track is actually playing.
 *
 * docs/YOUTUBE_CAPABILITY_MATRIX.md requires it visible and unobscured, with its own
 * controls and branding intact, so it is on the screen rather than behind the sleeve - the
 * cover says what the record is, and this says where the sound is coming from. Nothing is
 * drawn over it and it is never resized to nothing.
 */
@Composable
private fun YouTubePlayerCard(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Playing on YouTube",
            style = Aurora.typography.section,
            color = Aurora.colors.textTertiary,
        )
        Spacer(modifier = Modifier.height(Aurora.spacing.space2))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(Color.Black),
        ) {
            YouTubePlayerSurface(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun FavoriteButton(
    isFavorite: Boolean,
    title: String,
    onToggle: (Boolean) -> Unit,
) {
    val haptics = LocalHapticEngine.current
    // Springs out when it fills, so the tap feels answered rather than merely recorded.
    val scale by animateFloatAsState(
        targetValue = if (isFavorite) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "favourite",
    )

    GlassIconButton(
        onClick = {
            haptics.fire(if (isFavorite) HapticEvent.Tap else HapticEvent.Success)
            onToggle(!isFavorite)
        },
        icon = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
        contentDesc = if (isFavorite) "Remove $title from favorites" else "Add $title to favorites",
        selected = isFavorite,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    )
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
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
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
            modifier = Modifier.size(56.dp),
        )

        // The one filled control on the screen, and where the thumb lands. Its icon
        // animates inside it, so the circle stays put.
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
            size = 76.dp,
        )

        GlassIconButton(
            onClick = {
                haptics.fire(HapticEvent.SkipNext)
                onCommand(PlayerCommand.SkipNext)
            },
            icon = Icons.Rounded.SkipNext,
            contentDesc = "Next",
            modifier = Modifier.size(56.dp),
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
