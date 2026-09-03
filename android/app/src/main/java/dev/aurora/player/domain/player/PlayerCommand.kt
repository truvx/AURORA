package dev.aurora.player.domain.player

import dev.aurora.player.domain.models.MediaItem

sealed class PlayerCommand {
    data class Load(val track: MediaItem) : PlayerCommand()
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
}
