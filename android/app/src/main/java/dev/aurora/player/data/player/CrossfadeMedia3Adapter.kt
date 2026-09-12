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
    
    /**
     * Only the active player's events are exposed.
     *
     * During a crossfade both players are genuinely playing, so forwarding both would let
     * the outgoing track's position and completion events fight the incoming one. The same
     * unconditional merge in CompositePlayerAdapter is what froze playback at 0:00.
     */
    override val events: Flow<EngineEvent> = merge(
        primary.events.filter { activePlayer === primary },
        secondary.events.filter { activePlayer === secondary }
    )

    override fun load(track: MediaItem, uri: String, playWhenReady: Boolean, crossfadeDurationMs: Long) {
        crossfadeJob?.cancel()
        
        if (crossfadeDurationMs > 0 && activePlayer.exoPlayer.playbackState != androidx.media3.common.Player.STATE_IDLE) {
            val fadingOutPlayer = activePlayer
            val fadingInPlayer = inactivePlayer
            
            // Swap active players
            activePlayer = fadingInPlayer
            inactivePlayer = fadingOutPlayer
            
            // Start loading and playing new track on zero volume
            fadingInPlayer.setVolume(0f)
            fadingInPlayer.load(track, uri, playWhenReady, 0L)
            
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
            activePlayer.load(track, uri, playWhenReady, 0L)
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
