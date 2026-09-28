package dev.aurora.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.ui.components.GlassCard
import dev.aurora.player.ui.components.GlassIconButton
import dev.aurora.player.ui.components.GlassLevel
import dev.aurora.player.ui.components.TrackRow
import dev.aurora.player.ui.navigation.LocalChromeInsets
import dev.aurora.player.ui.theme.Aurora
import java.util.concurrent.TimeUnit

/**
 * What's new: releases from the artists the listener plays.
 *
 * Opening it is what marks them seen - there is no background job and no notification
 * permission, so "new" can only ever mean "newer than the last time you looked here".
 */
@Composable
fun WhatsNewScreen(
    releases: List<ResolvedRelease>,
    unseen: Set<String>,
    onPlay: (List<MediaItem>, Int) -> Unit,
    onBack: () -> Unit,
    onSeen: () -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    val spacing = Aurora.spacing

    DisposableEffect(Unit) {
        // Marked on leaving rather than on arrival, so the badges stay visible while the
        // list is being read.
        onDispose { onSeen() }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(
            top = spacing.space4,
            bottom = LocalChromeInsets.current.calculateBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.space3)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.space4),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(
                    icon = Icons.Rounded.ArrowBack,
                    contentDesc = "Back",
                    onClick = onBack
                )
                Text(
                    text = "What's new",
                    style = Aurora.typography.display,
                    color = Aurora.colors.textPrimary,
                    modifier = Modifier.padding(start = spacing.space3)
                )
            }
        }

        if (releases.isEmpty()) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.space4),
                    level = GlassLevel.Secondary
                ) {
                    Text(
                        text = "Nothing new yet. Releases from the artists you play show up here.",
                        style = Aurora.typography.body,
                        color = Aurora.colors.textSecondary
                    )
                }
            }
        } else {
            item {
                Column {
                    releases.forEachIndexed { index, card ->
                        TrackRow(
                            item = card.release.item,
                            onPlay = {
                                onPlay(releases.map { it.release.item }, index)
                            },
                            trailingLabel = relativeAge(card.release.publishedAtMs, now),
                            modifier = Modifier.padding(horizontal = spacing.space4)
                        )
                    }
                }
            }
        }
    }
}

/** "3 days ago" rather than a date: recency is the point of this list. */
internal fun relativeAge(publishedAtMs: Long, nowMs: Long): String {
    val days = TimeUnit.MILLISECONDS.toDays((nowMs - publishedAtMs).coerceAtLeast(0))
    return when {
        days <= 0 -> "Today"
        days == 1L -> "Yesterday"
        days < 7 -> "$days days ago"
        days < 14 -> "Last week"
        days < 60 -> "${days / 7} weeks ago"
        else -> "${days / 30} months ago"
    }
}
