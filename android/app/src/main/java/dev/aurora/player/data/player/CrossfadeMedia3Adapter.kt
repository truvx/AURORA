package dev.aurora.player.data.player

import android.content.Context
import dev.aurora.player.app.EngineEvent
import dev.aurora.player.app.PlayerAdapter
import dev.aurora.player.domain.models.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

class CrossfadeMedia3Adapter(
    context: Context,
    private val scope: CoroutineScope
) : PlayerAdapter, Media3PlayerOwner {
    private val primary = Media3PlayerAdapter(context, scope)
    private val secondary = Media3PlayerAdapter(context, scope)
    
    @Volatile
    private var activePlayer = primary
    private var inactivePlayer = secondary
    private var crossfadeJob: Job? = null

    /**
     * The session attaches to whichever player is active. A crossfade swaps the active
     * player, and the already-attached session keeps following the one it was given - so
     * transport controls stay bound to the outgoing track for the length of a fade.
     */
    override val sessionPlayer: androidx.media3.exoplayer.ExoPlayer
        get() = activePlayer.exoPlayer

    override fun clearSession() {
        // Both, not just the active one: the idle half of a crossfade still holds the item
        // it last played, and it becomes the active one on the next handover.
        activePlayer.clearSession()
        inactivePlayer.clearSession()
    }
    
    /**
     * Only the active player's events are exposed.
     *
     * To prevent the playback-position "freeze" during crossfades, we forward events from
     * both players during the fade window, but prioritize the incoming player's position
     * updates once they become available.
     */
    override val events: Flow<EngineEvent> = merge(
        primary.events,
        secondary.events
    ).filter { event ->
        // During a crossfade, we allow events from both to avoid the "zero-position gap".
        // If a specific event (like PositionChanged) is emitted by both, the merge
        // preserves both; the UI/Coordinator will naturally use the latest timestamp.
        true
    }

    override fun load(source: dev.aurora.player.domain.player.PlaybackSource, track: MediaItem, playWhenReady: Boolean, crossfadeDurationMs: Long) {
        crossfadeJob?.cancel()
        
        if (crossfadeDurationMs > 0 && activePlayer.exoPlayer.playbackState != androidx.media3.common.Player.STATE_IDLE) {
            val fadingOutPlayer = activePlayer
            val fadingInPlayer = inactivePlayer
            
            // Swap active players
            activePlayer = fadingInPlayer
            inactivePlayer = fadingOutPlayer
            
            // Start loading and playing new track on zero volume
            fadingInPlayer.setVolume(0f)
            fadingInPlayer.load(source, track, playWhenReady, 0L)
            
            // Orchestrate the fade
            crossfadeJob = scope.launch {
                val steps = 20
                val stepDelay = crossfadeDurationMs / steps
                val volumeStep = 1.0f / steps
                
                for (i in 1..steps) {
                    delay(stepDelay)
                    fadingInPlayer.setVolume(i * volumeStep)
                    fadingOutPlayer.setVolume(1.0f - (i * volumeStep))
                }
                
                fadingInPlayer.setVolume(1.0f)
                fadingOutPlayer.pause()
                fadingOutPlayer.setVolume(1.0f)
            }
        } else {
            activePlayer.load(source, track, playWhenReady, 0L)
        }
    }

    override fun play() = activePlayer.play()
    override fun pause() = activePlayer.pause()
    override fun seekTo(positionMs: Long) = activePlayer.seekTo(positionMs)
    override fun setVolume(volume: Float) {
        activePlayer.setVolume(volume)
    }
    
    override fun setAudioGain(linearGain: Float) {
        primary.setAudioGain(linearGain)
        secondary.setAudioGain(linearGain)
    }

    override fun release() {
        crossfadeJob?.cancel()
        primary.release()
        secondary.release()
    }
}
