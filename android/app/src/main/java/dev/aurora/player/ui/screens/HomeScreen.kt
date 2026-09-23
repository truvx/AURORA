package dev.aurora.player.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.aurora.player.domain.discovery.Mix
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.ui.components.GlassCard
import dev.aurora.player.ui.components.GlassLevel
import dev.aurora.player.ui.components.MixCard
import dev.aurora.player.ui.components.ReleaseCard
import dev.aurora.player.ui.components.SectionHeader
import dev.aurora.player.ui.components.TrackRow
import dev.aurora.player.ui.navigation.LocalChromeInsets
import dev.aurora.player.ui.theme.Aurora

/**
 * AURORA Home.
 *
 * Three sections, each built from the listener's own history: what the artists they play
 * have released, mixes seeded with those artists, and what they have been listening to.
 * Nothing here is filler - a section that has nothing real to show says why instead.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel? = null,
    onPlay: (MediaItem) -> Unit = {},
    onPlayMix: (Mix) -> Unit = {},
    onAddToQueue: (MediaItem) -> Unit = {},
    onOpenWhatsNew: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    profile: dev.aurora.player.data.discovery.Profile = dev.aurora.player.data.discovery.Profile(null, null),
    modifier: Modifier = Modifier,
) {
    val spacing = Aurora.spacing
    val typography = Aurora.typography
    val colors = Aurora.colors

    val releases by (viewModel?.releases?.collectAsState()
        ?: androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf<ReleasesState>(ReleasesState.NoHistory)
        })
    val mixes by (viewModel?.mixes?.collectAsState()
        ?: androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(emptyList<Mix>())
        })
    val recentlyPlayed by (viewModel?.recentlyPlayed?.collectAsState()
        ?: androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(emptyList<MediaItem>())
        })
    val hasUnseen = (releases as? ReleasesState.Ready)?.unseen?.isNotEmpty() == true
    val favorites by (viewModel?.favoriteIds?.collectAsState()
        ?: androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(emptySet<String>())
        })

    LazyColumn(
        modifier = modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(
            top = spacing.space6,
            bottom = LocalChromeInsets.current.calculateBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.space6)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.space4),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Listen Now",
                    style = typography.display,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                dev.aurora.player.ui.components.GlassIconButton(
                    icon = if (hasUnseen) Icons.Rounded.NotificationsActive else Icons.Rounded.Notifications,
                    contentDesc = if (hasUnseen) "What's new, new releases available" else "What's new",
                    onClick = onOpenWhatsNew
                )
                Spacer(modifier = Modifier.size(spacing.space2))
                ProfileAvatar(profile = profile, onClick = onOpenProfile)
            }
        }

        // --- Latest releases ------------------------------------------------------------
        item {
            Column {
                SectionHeader(
                    title = "Latest Releases",
                    modifier = Modifier.padding(horizontal = spacing.space4)
                )
                when (val state = releases) {
                    is ReleasesState.Ready -> {
                        if (state.releases.isEmpty()) {
                            Explainer(
                                "Nothing new from the artists you play, in the last six months."
                            )
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = spacing.space4),
                                horizontalArrangement = Arrangement.spacedBy(spacing.space3)
                            ) {
                                items(state.releases, key = { it.item.id }) { release ->
                                    ReleaseCard(
                                        item = release.item,
                                        isNew = release.item.id in state.unseen,
                                        onClick = { onPlay(release.item) }
                                    )
                                }
                            }
                        }
                    }
                    ReleasesState.Loading -> Explainer("Looking for new releases…")
                    // Each of these is a different problem with a different answer, so each
                    // says which one it is rather than showing an empty row.
                    ReleasesState.NoHistory ->
                        Explainer("Play something first, and new releases from those artists appear here.")
                    ReleasesState.NotConfigured ->
                        Explainer("New releases need a YouTube Data API key. Add one in Settings.")
                    ReleasesState.Unavailable ->
                        Explainer("Couldn't reach YouTube for new releases. It'll retry later.")
                }
            }
        }

        // --- Recommended mixes ----------------------------------------------------------
        if (mixes.isNotEmpty()) {
            item {
                Column {
                    SectionHeader(
                        title = "Recommended for You",
                        modifier = Modifier.padding(horizontal = spacing.space4)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = spacing.space4),
                        horizontalArrangement = Arrangement.spacedBy(spacing.space3)
                    ) {
                        items(mixes, key = { it.id }) { mix ->
                            MixCard(mix = mix, onClick = { onPlayMix(mix) })
                        }
                    }
                }
            }
        }

        // --- Recently played ------------------------------------------------------------
        item {
            SectionHeader(
                title = "Recently Played",
                modifier = Modifier.padding(horizontal = spacing.space4)
            )
        }
        if (recentlyPlayed.isEmpty()) {
            item { Explainer("Nothing played yet.") }
        } else {
            // One item, so the spacing between sections does not also push the rows apart.
            item {
                Column {
                    recentlyPlayed.forEach { item ->
                        TrackRow(
                            item = item,
                            onPlay = { onPlay(item) },
                            isFavorite = item.id in favorites,
                            onToggleFavorite = { viewModel?.setFavorite(item.id, item.id !in favorites) },
                            onAddToQueue = { onAddToQueue(item) },
                            modifier = Modifier.padding(horizontal = spacing.space4)
                        )
                    }
                }
            }
        }
    }
}

/** One quiet line saying why a section is empty. Always a reason, never a blank space. */
@Composable
private fun Explainer(text: String) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Aurora.spacing.space4),
        level = GlassLevel.Secondary
    ) {
        Text(
            text = text,
            style = Aurora.typography.body,
            color = Aurora.colors.textSecondary
        )
    }
}
