package dev.aurora.player.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.aurora.player.data.lyrics.LyricsRepository
import dev.aurora.player.domain.lyrics.LrcParser
import dev.aurora.player.domain.lyrics.Lyrics
import dev.aurora.player.ui.accessibility.LocalAccessibilityPreferences
import dev.aurora.player.ui.components.GlassIconButton
import dev.aurora.player.ui.theme.Aurora

/**
 * The words, over the record they belong to.
 *
 * Timed lyrics follow the playhead: the line being sung is lit and the rest recede, which
 * is the only reason to show a lyric in a player rather than in a browser. Where the source
 * has no timings the words are shown as a block instead - honestly static rather than
 * pretending to a sync AURORA does not have.
 *
 * Every state says which one it is. "No lyrics found" and "couldn't reach the service" are
 * different facts and a listener can act on the difference; a single blank panel for both
 * is the failure this screen exists to avoid.
 */
@Composable
fun LyricsSheet(
    state: LyricsRepository.Result?,
    positionMs: Long,
    title: String?,
    artist: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Aurora.colors
    val spacing = Aurora.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundPrimary)
            .systemBarsPadding()
            .padding(horizontal = spacing.space5),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = spacing.space2),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(
                onClick = onClose,
                icon = Icons.Rounded.KeyboardArrowDown,
                contentDesc = "Close lyrics",
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title ?: "Lyrics",
                    style = Aurora.typography.label,
                    color = colors.textPrimary,
                )
                if (artist != null) {
                    Text(
                        text = artist.uppercase(),
                        style = Aurora.typography.section,
                        color = colors.textTertiary,
                    )
                }
            }
            // Balances the row so the title stays centred. Not a control, so not in the tree.
            Box(modifier = Modifier.padding(spacing.space6))
        }

        when (state) {
            null -> Message("Looking for the words…")
            is LyricsRepository.Result.Unavailable ->
                Message("Couldn't reach the lyrics service. It'll retry next time.")
            LyricsRepository.Result.None ->
                Message("No lyrics for this recording.")
            LyricsRepository.Result.Unidentified ->
                Message("This upload couldn't be read as a song, so there was nothing to look up.")
            is LyricsRepository.Result.Found -> when (val lyrics = state.lyrics) {
                Lyrics.Instrumental -> Message("Instrumental — this recording has no words.")
                is Lyrics.Plain -> PlainLyrics(lyrics.text, state.source)
                is Lyrics.Synced -> SyncedLyrics(lyrics, positionMs, state.source)
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = Aurora.typography.body,
            color = Aurora.colors.textSecondary,
        )
    }
}

@Composable
private fun PlainLyrics(text: String, source: String) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = text,
            style = Aurora.typography.body,
            color = Aurora.colors.textPrimary,
            modifier = Modifier.padding(vertical = Aurora.spacing.space6),
        )
        Attribution(source)
    }
}

@Composable
private fun SyncedLyrics(lyrics: Lyrics.Synced, positionMs: Long, source: String) {
    val active = LrcParser.activeIndex(lyrics.lines, positionMs)
    val listState = rememberLazyListState()
    val reducedMotion = LocalAccessibilityPreferences.current.reducedMotion

    /*
     * The current line is kept a third of the way down rather than at the top, so the
     * lines about to be sung are already on screen. A lyric that scrolls the moment you
     * need to read ahead is worse than one that does not scroll at all.
     */
    LaunchedEffect(active) {
        if (active < 0) return@LaunchedEffect
        val target = (active - 2).coerceAtLeast(0)
        if (reducedMotion) listState.scrollToItem(target) else listState.animateScrollToItem(target)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                // The sung line is announced as it changes rather than the whole song.
                liveRegion = LiveRegionMode.Polite
                contentDescription = lyrics.lines.getOrNull(active)?.text.orEmpty()
            },
        contentPadding = PaddingValues(vertical = Aurora.spacing.space10),
        verticalArrangement = Arrangement.spacedBy(Aurora.spacing.space3),
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            LyricRow(text = line.text, isActive = index == active)
        }
        item { Attribution(source) }
    }
}

/**
 * One line, lit or not.
 *
 * The difference is weight and colour together, never colour alone - the sung line has to
 * be findable without colour vision, and Bold against Regular is the contrast the family
 * actually ships.
 */
@Composable
private fun LyricRow(text: String, isActive: Boolean) {
    val reducedMotion = LocalAccessibilityPreferences.current.reducedMotion
    val duration = if (reducedMotion) 0 else Aurora.motion.durationQuick

    val color by animateColorAsState(
        targetValue = if (isActive) Aurora.colors.textPrimary else Aurora.colors.textTertiary,
        animationSpec = tween(duration),
        label = "lyric colour",
    )
    val scale by animateFloatAsState(
        targetValue = if (isActive || reducedMotion) 1f else 0.98f,
        animationSpec = tween(duration),
        label = "lyric scale",
    )

    Text(
        text = text.ifBlank { "·" },
        style = Aurora.typography.trackTitleLarge,
        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
        color = color,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
        },
    )
}

/** Whose database answered. A condition of using it, and the listener's right to know. */
@Composable
private fun Attribution(source: String) {
    Text(
        text = "Lyrics from $source",
        style = Aurora.typography.caption,
        color = Aurora.colors.textTertiary,
        modifier = Modifier.padding(vertical = Aurora.spacing.space6),
    )
}
