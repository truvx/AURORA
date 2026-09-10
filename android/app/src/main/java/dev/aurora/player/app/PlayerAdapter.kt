package dev.aurora.player.app

import dev.aurora.player.domain.models.MediaItem
import kotlinx.coroutines.flow.Flow

sealed class EngineEvent {
    object Prepared : EngineEvent()
    object Started : EngineEvent()
    object Paused : EngineEvent()
    data class BufferingChanged(val isBuffering: Boolean) : EngineEvent()
    data class PositionChanged(val elapsed: Long, val duration: Long?, val buffered: Long?) : EngineEvent()
    object SeekStarted : EngineEvent()
    object SeekCompleted : EngineEvent()
    object TrackCompleted : EngineEvent()
    data class Error(val error: Throwable) : EngineEvent()
}

interface PlayerAdapter {
    val events: Flow<EngineEvent>
    
    fun load(track: MediaItem, uri: String, playWhenReady: Boolean = true, crossfadeDurationMs: Long = 0L)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
    fun setAudioGain(linearGain: Float)
    fun release()
}
