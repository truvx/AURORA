package dev.aurora.player.data.player

import dev.aurora.player.app.EngineEvent
import dev.aurora.player.app.PlayerAdapter
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

class CompositePlayerAdapter(
    private val localAdapter: PlayerAdapter,
    private val youtubeAdapter: PlayerAdapter
) : PlayerAdapter {

    override val events: Flow<EngineEvent> = merge(localAdapter.events, youtubeAdapter.events)

    private var activeAdapter: PlayerAdapter? = null

    override fun load(track: MediaItem, uri: String) {
        val nextAdapter = if (track.provider == ProviderKind.YOUTUBE) {
            youtubeAdapter
        } else {
            localAdapter
        }

        if (activeAdapter != nextAdapter) {
            activeAdapter?.pause()
            activeAdapter = nextAdapter
        }
        
        activeAdapter?.load(track, uri)
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
