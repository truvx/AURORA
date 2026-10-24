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
import kotlinx.coroutines.flow.map
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
     * During a crossfade both players emit positions: the incoming player from 0 upward,
     * the outgoing player from near the end of the old track downward. Merging both raw
     * lets a stale outgoing-player position overwrite the incoming player's, freezing the
     * playhead at the old track's end. We track which adapter last emitted a position and,
     * while a crossfade is active, drop positions from anyone but the active (incoming)
     * player so the playhead always reflects the track being heard.
     */
    @Volatile
    private var lastPositionSource: PlayerAdapter? = null

    override val events: Flow<EngineEvent> = merge(
        primary.events.map { event: EngineEvent ->
            if (event is EngineEvent.PositionChanged) lastPositionSource = primary
            event
        },
        secondary.events.map { event: EngineEvent ->
            if (event is EngineEvent.PositionChanged) lastPositionSource = secondary
            event
        }
    ).filter { event ->
        if (event is EngineEvent.PositionChanged && crossfadeJob?.isActive == true) {
            lastPositionSource === activePlayer
        } else {
            true
        }
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
