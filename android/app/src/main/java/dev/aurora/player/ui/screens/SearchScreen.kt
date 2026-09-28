package dev.aurora.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.ui.components.GlassCard
import dev.aurora.player.ui.components.GlassLevel
import dev.aurora.player.ui.components.SectionHeader
import dev.aurora.player.ui.components.TrackRow
import dev.aurora.player.ui.navigation.LocalChromeInsets
import dev.aurora.player.ui.theme.Aurora

/**
 * Search: the listener's own library, and YouTube.
 *
 * Library matches appear as they type. YouTube is asked only when they submit, because a
 * search there spends a hundredth of the day's quota every time.
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel? = null,
    /**
     * Play a list from one of its tracks.
     *
     * The list rather than the one item, so the queue has somewhere for next and previous
     * to go. Tapping a row means "play this list from here".
     */
    onPlay: (List<MediaItem>, Int) -> Unit = { _, _ -> },
    onAddToQueue: (MediaItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val spacing = Aurora.spacing
    val colors = Aurora.colors

    val query by (viewModel?.query?.collectAsState()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") })
    val localResults by (viewModel?.localResults?.collectAsState()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emptyList<MediaItem>()) })
    val remote by (viewModel?.remote?.collectAsState()
        ?: androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf<RemoteSearchState>(RemoteSearchState.Idle)
        })
    val trending by (viewModel?.trendingNow?.collectAsState()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emptyList<MediaItem>()) })

    LazyColumn(
        modifier = modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(
            top = spacing.space6,
            bottom = LocalChromeInsets.current.calculateBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.space4)
    ) {
        item {
            Text(
                text = "Search",
                style = Aurora.typography.display,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = spacing.space4)
            )
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel?.onQueryChange(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.space4),
                placeholder = { Text("Songs, artists, albums", color = colors.textTertiary) },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.textSecondary)
                },
                singleLine = true,
                // Submit is what reaches YouTube, so the key says so.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel?.submit() }),
                shape = RoundedCornerShape(percent = 50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = colors.surfaceGlassSecondary,
                    unfocusedContainerColor = colors.surfaceGlassSecondary,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.accentPrimary
                )
            )
        }

        if (query.isBlank()) {
            // Somewhere to start, rather than an empty screen waiting to be typed into.
            item {
                SectionHeader(
                    title = "Trending in ${viewModel?.regionName ?: "your region"}",
                    modifier = Modifier.padding(horizontal = spacing.space4)
                )
            }
            if (trending.isEmpty()) {
                item { Hint("Trending needs a YouTube API key, or a connection.") }
            } else {
                item { TrackList(trending, onPlay, onAddToQueue) }
            }
            return@LazyColumn
        }

        item {
            SectionHeader(
                title = "In your library",
                modifier = Modifier.padding(horizontal = spacing.space4)
            )
        }
        if (localResults.isEmpty()) {
            item { Hint("No matches in your library.") }
        } else {
            item { TrackList(localResults, onPlay, onAddToQueue) }
        }

        item {
            SectionHeader(
                title = "YouTube",
                modifier = Modifier.padding(horizontal = spacing.space4)
            )
        }
        when (val state = remote) {
            RemoteSearchState.Idle -> item { Hint("Press search to look on YouTube.") }
            RemoteSearchState.Searching -> item { Hint("Searching YouTube…") }
            is RemoteSearchState.Results ->
                if (state.items.isEmpty()) {
                    item { Hint("Nothing on YouTube for that.") }
                } else {
                    item { TrackList(state.items, onPlay, onAddToQueue) }
                }
            RemoteSearchState.NotConfigured ->
                item { Hint("Searching YouTube needs a Data API key. Add one in Settings.") }
            RemoteSearchState.Rejected ->
                item { Hint("YouTube turned the request down. Check the key in Settings, that YouTube Data API v3 is enabled for it, and that today's quota isn't spent.") }
            RemoteSearchState.Unavailable ->
                item { Hint("Couldn't reach YouTube. Check your connection.") }
        }
    }
}

@Composable
private fun TrackList(
    items: List<MediaItem>,
    onPlay: (List<MediaItem>, Int) -> Unit,
    onAddToQueue: (MediaItem) -> Unit,
) {
    Column {
        items.forEachIndexed { index, item ->
            TrackRow(
                item = item,
                onPlay = { onPlay(items, index) },
                onAddToQueue = { onAddToQueue(item) },
                modifier = Modifier.padding(horizontal = Aurora.spacing.space4)
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Aurora.spacing.space4),
        level = GlassLevel.Secondary
    ) {
        Text(text = text, style = Aurora.typography.body, color = Aurora.colors.textSecondary)
    }
}
