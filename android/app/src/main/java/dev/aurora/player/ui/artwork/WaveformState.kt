package dev.aurora.player.ui.artwork

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import dev.aurora.player.data.audio.WaveformRepository
import dev.aurora.player.domain.audio.Waveform
import dev.aurora.player.domain.models.MediaItem

/** Supplied by the composition root; absent in previews and in tests that do not need it. */
val LocalWaveformRepository = staticCompositionLocalOf<WaveformRepository?> { null }

/**
 * The measured shape of a track, or null when there is none to measure.
 *
 * Null is the answer for every YouTube track and stays null: the scrubber draws plain bars
 * for it, which is what "AURORA has not heard this audio" looks like. The first local file
 * costs a decode; every play after that reads the answer back.
 */
@Composable
fun rememberWaveform(track: MediaItem?): State<Waveform?> {
    val repository = LocalWaveformRepository.current
    val state = remember(track?.id) { mutableStateOf<Waveform?>(null) }

    LaunchedEffect(track?.id, repository) {
        state.value = null
        if (repository == null || track == null) return@LaunchedEffect
        state.value = repository.waveformFor(track)
    }

    return state
}
