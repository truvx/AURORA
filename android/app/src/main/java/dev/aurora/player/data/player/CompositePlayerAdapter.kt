package dev.aurora.player.data.player

import dev.aurora.player.app.EngineEvent
import dev.aurora.player.app.PlayerAdapter
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.merge

class CompositePlayerAdapter(
    private val localAdapter: PlayerAdapter,
    private val youtubeAdapter: PlayerAdapter
) : PlayerAdapter {

    // Volatile: load() runs on the caller's thread while the flows are collected elsewhere,
    // and the filters below read this on every emission.
    @Volatile
    private var activeAdapter: PlayerAdapter? = null

    /**
     * Only the active adapter's events are forwarded.
     *
     * Merging both unconditionally meant the YouTube IFrame's JavaScript timer - which runs
     * every 500ms from the moment its player is ready, reporting 0/0 whenever no video is
     * loaded - overwrote the position of a locally playing track twice a second. Playback
     * looked frozen at 0:00 while the audio was actually playing.
     */
    override val events: Flow<EngineEvent> = merge(
        localAdapter.events.filter { activeAdapter === localAdapter },
        youtubeAdapter.events.filter { activeAdapter === youtubeAdapter }
    )

    override fun load(track: MediaItem, uri: String, playWhenReady: Boolean, crossfadeDurationMs: Long) {
        val nextAdapter = if (track.provider == ProviderKind.YOUTUBE) {
            youtubeAdapter
        } else {
            localAdapter
        }

        if (activeAdapter != nextAdapter) {
            activeAdapter?.pause()
            activeAdapter = nextAdapter
        }
        
        activeAdapter?.load(track, uri, playWhenReady, crossfadeDurationMs)
    }

    override fun play() {
        activeAdapter?.play()
    }

    override fun pause() {
        activeAdapter?.pause()
    }

    override fun seekTo(positionMs: Long) {
        activeAdapter?.seekTo(positionMs)
    }

    override fun setVolume(volume: Float) {
        localAdapter.setVolume(volume)
        youtubeAdapter.setVolume(volume)
    }

    override fun setAudioGain(linearGain: Float) {
        localAdapter.setAudioGain(linearGain)
        youtubeAdapter.setAudioGain(linearGain)
    }

    override fun release() {
        localAdapter.release()
        youtubeAdapter.release()
    }
}
