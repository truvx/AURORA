package dev.aurora.player.ui.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import dev.aurora.player.data.lyrics.LyricsRepository
import dev.aurora.player.domain.discovery.TrackIdentity

/** Supplied by the composition root; absent in previews and in tests that do not need it. */
val LocalLyricsRepository = staticCompositionLocalOf<LyricsRepository?> { null }

/**
 * The words for a track, looked up once per track.
 *
 * Null while the lookup is in flight, which is what the sheet renders as "looking". The
 * identity passed in is the resolved one where there is one - for a YouTube upload that is
 * "keshi" and "Soft Spot" rather than "keshi - Soft Spot (Official Music Video)" and
 * "KeshiVEVO", and searching a lyrics database for the latter finds nothing.
 */
@Composable
fun rememberLyrics(
    identity: TrackIdentity?,
    durationMs: Long?,
): State<LyricsRepository.Result?> {
    val repository = LocalLyricsRepository.current
    val state = remember(identity) { mutableStateOf<LyricsRepository.Result?>(null) }

    LaunchedEffect(identity, repository) {
        state.value = null
        if (repository == null) return@LaunchedEffect
        state.value = repository.lyricsFor(identity, durationMs)
    }

    return state
}
