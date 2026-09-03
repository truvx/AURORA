package dev.aurora.player.data.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.aurora.player.app.EngineEvent
import dev.aurora.player.app.PlayerAdapter
import dev.aurora.player.domain.models.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.common.audio.AudioProcessor

class Media3PlayerAdapter(
    context: Context,
    private val scope: CoroutineScope
) : PlayerAdapter {

    val normalizationProcessor = NormalizationAudioProcessor()

    val renderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean
        ): AudioSink {
            return DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf<AudioProcessor>(normalizationProcessor))
                .build()
        }
    }

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context, renderersFactory)
        .setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true // handleAudioFocus
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
    override val events: Flow<EngineEvent> = _events

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> _events.tryEmit(EngineEvent.Prepared)
                    Player.STATE_ENDED -> _events.tryEmit(EngineEvent.TrackCompleted)
                    Player.STATE_BUFFERING -> _events.tryEmit(EngineEvent.BufferingChanged(true))
                }
                
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                    _events.tryEmit(EngineEvent.BufferingChanged(false))
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    _events.tryEmit(EngineEvent.Started)
                } else if (exoPlayer.playbackState != Player.STATE_ENDED) {
                    _events.tryEmit(EngineEvent.Paused)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                    _events.tryEmit(EngineEvent.SeekCompleted)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _events.tryEmit(EngineEvent.Error(error))
            }
        })

        // Poll for position updates
        scope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    val duration = if (exoPlayer.duration == androidx.media3.common.C.TIME_UNSET) null else exoPlayer.duration
                    _events.tryEmit(
                        EngineEvent.PositionChanged(
                            elapsed = exoPlayer.currentPosition,
                            duration = duration,
                            buffered = exoPlayer.bufferedPosition
                        )
                    )
                }
                delay(200) // 200ms updates
            }
        }
    }

    override fun load(track: MediaItem, uri: String) {
        val media3Item = Media3Item.Builder()
            .setMediaId(track.id)
            .setUri(Uri.parse(uri))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(track.artworkUri?.let { Uri.parse(it) })
                    .build()
            )
            .build()

        exoPlayer.setMediaItem(media3Item)
        exoPlayer.prepare()
    }

    override fun play() {
        exoPlayer.play()
    }

    override fun pause() {
        exoPlayer.pause()
    }

    override fun seekTo(positionMs: Long) {
        _events.tryEmit(EngineEvent.SeekStarted)
        exoPlayer.seekTo(positionMs)
    }

    override fun setVolume(volume: Float) {
        exoPlayer.volume = volume
    }

    override fun setAudioGain(linearGain: Float) {
        normalizationProcessor.setLinearGain(linearGain)
    }

    override fun release() {
        exoPlayer.release()
    }
}
