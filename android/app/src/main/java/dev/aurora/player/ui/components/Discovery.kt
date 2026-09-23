package dev.aurora.player.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aurora.player.domain.discovery.Mix
import dev.aurora.player.domain.discovery.MixMood
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.ui.haptics.HapticEvent
import dev.aurora.player.ui.haptics.LocalHapticEngine
import dev.aurora.player.ui.theme.Aurora

/** Card width for the horizontal rows on Home. Three fit across a phone, as in the reference. */
private val CardWidth = 132.dp

/**
 * A section title, with an optional way to see the rest.
 *
 * The action only appears when there is genuinely more than the row shows - a "more" that
 * leads to the same items the user is already looking at is noise.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = Aurora.colors
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = Aurora.spacing.space3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Small caps, letter-spaced, quiet: in the reference these label a section without
        // competing with the artwork and titles inside it.
        Text(
            text = title.uppercase(),
            style = Aurora.typography.section,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(onClick = onAction)
                    .padding(horizontal = Aurora.spacing.space3, vertical = Aurora.spacing.space2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = actionLabel,
                    style = Aurora.typography.label,
                    color = colors.textSecondary
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Press feedback shared by the cards: it starts on touch-down, not on release. */
@Composable
private fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
        label = "card press"
    )
    val haptics = LocalHapticEngine.current
    return this
        .graphicsLayer {
            scaleX = 1f - 0.03f * press
            scaleY = 1f - 0.03f * press
        }
        .clickable(interactionSource = interaction, indication = null) {
            haptics.fire(HapticEvent.Tap)
            onClick()
        }
}

/**
 * A recent release from an artist the listener plays.
 *
 * "NEW" is only shown for something they have not seen yet, so the badge keeps meaning
 * something after the first week.
 */
@Composable
fun ReleaseCard(
    item: MediaItem,
    isNew: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Aurora.colors
    Column(
        modifier = modifier
            .width(CardWidth)
            .pressable(onClick)
            .semantics(mergeDescendants = true) {}
    ) {
        Box {
            ArtworkSurface(
                altText = "",
                artworkUri = item.artworkUri,
                modifier = Modifier.size(CardWidth).clearAndSetSemantics {}
            )
            if (isNew) {
                Text(
                    text = "NEW",
                    style = Aurora.typography.caption,
                    color = colors.textInverse,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(Aurora.spacing.space2)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.controlPrimary)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(Aurora.spacing.space2))
        Text(
            text = item.title,
            style = Aurora.typography.trackTitle,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = item.artist ?: "Unknown artist",
            style = Aurora.typography.trackArtist,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A mood mix, drawn rather than photographed.
 *
 * There is no artwork for a mix that has not been generated yet, and inventing a cover
 * would be a picture of nothing. The gradient is the mood, and the subtitle says plainly
 * whether it was built from the listener's own artists.
 */
@Composable
fun MixCard(
    mix: Mix,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Aurora.colors
    Column(
        modifier = modifier
            .width(CardWidth)
            .pressable(onClick)
            .semantics(mergeDescendants = true) {}
    ) {
        Box(
            modifier = Modifier
                .size(CardWidth)
                .clip(Aurora.shapes.artwork)
                .background(Brush.linearGradient(mix.mood.gradient()))
        ) {
            Icon(
                imageVector = mix.mood.icon(),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.align(Alignment.TopStart).padding(Aurora.spacing.space3).size(22.dp)
            )
            Text(
                text = mix.title,
                style = Aurora.typography.title,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(Aurora.spacing.space3)
            )
        }
        Spacer(modifier = Modifier.height(Aurora.spacing.space2))
        Text(
            text = mix.subtitle,
            style = Aurora.typography.body,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Each mood's own colours, so the row reads as six different things at a glance. */
private fun MixMood.gradient(): List<Color> = when (this) {
    MixMood.CHILL -> listOf(Color(0xFF2B5876), Color(0xFF4E4376))
    MixMood.FOCUS -> listOf(Color(0xFF16222A), Color(0xFF3A6073))
    MixMood.WORKOUT -> listOf(Color(0xFF8E2DE2), Color(0xFFDA2222))
    MixMood.LATE_NIGHT -> listOf(Color(0xFF0F2027), Color(0xFF203A43))
    MixMood.DISCOVER -> listOf(Color(0xFF654EA3), Color(0xFFDA98B4))
    MixMood.FEEL_GOOD -> listOf(Color(0xFFF7971E), Color(0xFFFFD200))
}

private fun MixMood.icon(): ImageVector = when (this) {
    MixMood.CHILL -> Icons.Rounded.Spa
    MixMood.FOCUS -> Icons.Rounded.CenterFocusStrong
    MixMood.WORKOUT -> Icons.Rounded.FitnessCenter
    MixMood.LATE_NIGHT -> Icons.Rounded.DarkMode
    MixMood.DISCOVER -> Icons.Rounded.Explore
    MixMood.FEEL_GOOD -> Icons.Rounded.WbSunny
}

/** One track in a list: artwork, title, artist, and the overflow menu from the references. */
@Composable
fun TrackRow(
    item: MediaItem,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    trailingLabel: String? = null,
) {
    val colors = Aurora.colors
    val haptics = LocalHapticEngine.current
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .pressable(onPlay)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkSurface(
                altText = "",
                artworkUri = item.artworkUri,
                modifier = Modifier.size(48.dp).clearAndSetSemantics {}
            )
            Spacer(modifier = Modifier.width(Aurora.spacing.space3))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = Aurora.typography.trackTitle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(item.artist ?: "Unknown artist", trailingLabel).joinToString(" · "),
                    style = Aurora.typography.trackArtist,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (onAddToQueue != null || onToggleFavorite != null) {
            Box {
                GlassIconButton(
                    icon = Icons.Rounded.MoreHoriz,
                    contentDesc = "More options for ${item.title}",
                    onClick = {
                        haptics.fire(HapticEvent.Tap)
                        menuOpen = true
                    }
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    onAddToQueue?.let { addToQueue ->
                        DropdownMenuItem(
                            text = { Text("Add to queue") },
                            onClick = {
                                menuOpen = false
                                addToQueue()
                            }
                        )
                    }
                    onToggleFavorite?.let { toggle ->
                        DropdownMenuItem(
                            text = { Text(if (isFavorite) "Remove from favorites" else "Add to favorites") },
                            onClick = {
                                menuOpen = false
                                toggle()
                            }
                        )
                    }
                }
            }
        }
    }
}
