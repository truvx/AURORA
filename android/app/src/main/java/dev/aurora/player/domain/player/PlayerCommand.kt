package dev.aurora.player.domain.player

import dev.aurora.player.domain.models.MediaItem

sealed class PlayerCommand {
    data class Load(val track: MediaItem) : PlayerCommand()

    /**
     * Play a list from one of its tracks.
     *
     * Distinct from [Load], which is one track arriving on its own and joins the queue
     * without disturbing it. A list is a *context*: tapping the third song of an album
     * means "play this album from here", so it replaces the queue. Without it, every entry
     * point loaded a single track, a fresh play left nothing to skip to, and next and
     * previous were dead controls on every surface - in the app, on the notification, on
     * the lock screen and over a headset.
     */
    data class PlayList(val items: List<MediaItem>, val startIndex: Int) : PlayerCommand()
    object Play : PlayerCommand()
    object Pause : PlayerCommand()
    data class Seek(val target: Long) : PlayerCommand()
    object SkipNext : PlayerCommand()
    object SkipPrevious : PlayerCommand()
    object Replay : PlayerCommand()
    data class SetShuffle(val mode: ShuffleMode) : PlayerCommand()
    data class SetRepeat(val mode: RepeatMode) : PlayerCommand()
    data class AddToQueue(val track: MediaItem) : PlayerCommand()
    data class RemoveFromQueue(val trackId: String) : PlayerCommand()
    data class MoveInQueue(val from: Int, val to: Int) : PlayerCommand()
    object ClearQueue : PlayerCommand()
    data class SetVolume(val volume: Float) : PlayerCommand()
    data class SetLoudnessPreference(val preference: dev.aurora.player.domain.audio.LoudnessPreference) : PlayerCommand()
    object Reload : PlayerCommand()
    object Stop : PlayerCommand()
    data class SetCrossfade(val durationMs: Long) : PlayerCommand()
    data class SetEqEnabled(val enabled: Boolean) : PlayerCommand()
}
