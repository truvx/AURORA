package dev.aurora.player.domain.player

import dev.aurora.player.domain.models.MediaItem

enum class PlaybackStatus {
    Idle, Loading, Ready, Playing, Paused, Buffering, Seeking, Completed, Error
}

enum class AudioFocusState {
    Unknown, Gained, LostTransiently, LostPermanently, Ducking
}

data class PlaybackPosition(
    val elapsed: Long,
    val duration: Long?, // null means Unknown
    val buffered: Long?
)

data class PlayerState(
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val currentTrack: MediaItem? = null,
    val position: PlaybackPosition = PlaybackPosition(0, null, null),
    val queue: QueueState = QueueState(),
    val volume: Float = 1.0f,
    val audioFocus: AudioFocusState = AudioFocusState.Unknown,
    val lastError: Throwable? = null,
    val loudnessPreference: dev.aurora.player.domain.audio.LoudnessPreference = dev.aurora.player.domain.audio.LoudnessPreference.Normal,
    val appliedNormalization: dev.aurora.player.domain.audio.AppliedNormalization? = null,
    val providerCapabilities: dev.aurora.player.domain.audio.ProviderAudioCapabilities? = null,
    val crossfadeDurationMs: Long = 0L,
    val isEqEnabled: Boolean = false
)
