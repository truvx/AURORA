package dev.aurora.player.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    /** A mark for the section, as in the reference. Decorative: the title already names it. */
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    /**
     * What the section holds, in a line under its name.
     *
     * A count, or a sentence - never both, and never a figure AURORA cannot actually
     * count. An empty section says so in its own body rather than here.
     */
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = Aurora.colors
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = Aurora.spacing.space3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(Aurora.spacing.space3))
        }
        Column(modifier = Modifier.weight(1f)) {
        // Small caps, letter-spaced, quiet: in the reference these label a section without
        // competing with the artwork and titles inside it.
        Text(
            text = title.uppercase(),
            style = Aurora.typography.section,
            color = colors.textSecondary
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = Aurora.typography.caption,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        }
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
    val press by rememberPressProgress(interaction)
    // Read here: graphicsLayer's lambda is not a composable scope.
    val pressDepth = Aurora.motion
    val haptics = LocalHapticEngine.current
    return this
        .graphicsLayer {
            scaleX = 1f - pressDepth.pressScaleSurface * press
            scaleY = 1f - pressDepth.pressScaleSurface * press
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
    title: String,
    artist: String,
    coverUrl: String?,
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
                artworkUri = coverUrl,
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
                        /*
                         * Tighter than the 4-unit scale, deliberately: this is the inside
                         * of a badge sitting on artwork, where a 4dp inset already reads
                         * as a gap around the word rather than as its box.
                         */
                        .padding(horizontal = Aurora.spacing.space1 + 2.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(Aurora.spacing.space2))
        Text(
            text = title,
            style = Aurora.typography.trackTitle,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = artist,
            style = Aurora.typography.trackArtist,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * What a section says when it has nothing to show.
 *
 * Always a reason, never a blank space - and where the reason has an answer, the answer is
 * a button rather than a sentence telling the listener where to go and leaving them to
 * find it. The mark repeats the section's own, so the notice reads as that section being
 * empty rather than as an error about the page.
 */
@Composable
fun SectionNotice(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    headline: String,
    detail: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    /** Announce it when it replaces something, e.g. after a retry. */
    announce: Boolean = false,
) {
    val colors = Aurora.colors
    val spacing = Aurora.spacing
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (announce) {
                    Modifier.semantics {
                        liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                    }
                } else {
                    Modifier
                }
            ),
        level = GlassLevel.Secondary
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(spacing.space3))
                Text(
                    text = headline,
                    style = Aurora.typography.trackTitle,
                    color = colors.textPrimary
                )
            }
            Spacer(modifier = Modifier.height(spacing.space1))
            Text(
                text = detail,
                style = Aurora.typography.body,
                color = colors.textSecondary
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(spacing.space3))
                GlassButton(
                    text = actionLabel,
                    onClick = onAction,
                    style = GlassButtonStyle.Secondary
                )
            }
        }
    }
}

/**
 * A row of cards that have not arrived yet.
 *
 * The shape of what is coming, so the page does not jump when it lands, and so waiting
 * looks different from having nothing. Static on purpose: a shimmer is motion spent on a
 * placeholder, and it would need a reduced-motion variant to say the same thing.
 */
@Composable
fun CardRowSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 3,
    contentDescription: String = "Loading",
) {
    val colors = Aurora.colors
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            this.contentDescription = contentDescription
            liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
        },
        horizontalArrangement = Arrangement.spacedBy(Aurora.spacing.space3)
    ) {
        repeat(count) {
            Column(modifier = Modifier.width(CardWidth)) {
                Box(
                    modifier = Modifier
                        .size(CardWidth)
                        .clip(Aurora.shapes.card)
                        .background(colors.surfaceGlassSecondary)
                )
                Spacer(modifier = Modifier.height(Aurora.spacing.space2))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.surfaceGlassSecondary)
                )
                Spacer(modifier = Modifier.height(Aurora.spacing.space1))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.surfaceGlassSecondary)
                )
            }
        }
    }
}

/**
 * A playlist the listener made, as a card in the Home row.
 *
 * No cover, because a playlist has none. The reference fills these with generated
 * gradients; a gradient here would be decoration standing in for a record sleeve that does
 * not exist, so the card carries its mark and says how many tracks are in it instead - the
 * one fact about a playlist worth knowing before opening it.
 */
@Composable
fun PlaylistCard(
    name: String,
    trackCount: Int,
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
        GlassSurface(
            modifier = Modifier.size(CardWidth),
            level = GlassLevel.Secondary,
            shape = Aurora.shapes.card,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.QueueMusic,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.align(Alignment.Center).size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(Aurora.spacing.space2))
        Text(
            text = name,
            style = Aurora.typography.trackTitle,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "$trackCount ${if (trackCount == 1) "track" else "tracks"}",
            style = Aurora.typography.trackArtist,
            color = colors.textSecondary,
            maxLines = 1
        )
    }
}

/**
 * An artist the listener plays, as a way into everything of theirs.
 *
 * Round, because a circle reads as a person and a square reads as a record - the same
 * distinction every music app makes, and the reason an artist row does not need a label
 * saying it is one.
 */
@Composable
fun ArtistCard(
    name: String,
    artworkUri: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(CardWidth)
            .pressable(onClick)
            .semantics(mergeDescendants = true) { contentDescription = name },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkSurface(
            altText = "",
            artworkUri = artworkUri,
            modifier = Modifier
                .size(CardWidth)
                .clip(CircleShape)
                .clearAndSetSemantics {}
        )
        Spacer(modifier = Modifier.height(Aurora.spacing.space2))
        Text(
            text = name,
            style = Aurora.typography.trackTitle,
            color = Aurora.colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
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
