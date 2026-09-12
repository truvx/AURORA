package dev.aurora.player.app

import android.util.Log

import dev.aurora.player.domain.player.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlayerCoordinator(
    private val adapter: PlayerAdapter,
    private val resolver: TrackResolver,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var targetPlayIntent = false

    init {
        scope.launch {
            adapter.events.collect { event ->
                handleEngineEvent(event)
            }
        }
    }

    fun dispatch(command: PlayerCommand) {
        Log.d(TAG, "dispatch: $command")
        when (command) {
            is PlayerCommand.Load -> loadTrack(command.track, playWhenReady = false)
            is PlayerCommand.Play -> play()
            is PlayerCommand.Pause -> pause()
            is PlayerCommand.Seek -> seekTo(command.target)
            is PlayerCommand.SkipNext -> skipNext()
            is PlayerCommand.SkipPrevious -> skipPrevious()
            is PlayerCommand.Replay -> replay()
            is PlayerCommand.SetShuffle -> setShuffle(command.mode)
            is PlayerCommand.SetRepeat -> setRepeat(command.mode)
            is PlayerCommand.AddToQueue -> addToQueue(command.track)
            is PlayerCommand.RemoveFromQueue -> removeFromQueue(command.trackId)
            is PlayerCommand.MoveInQueue -> moveInQueue(command.from, command.to)
            is PlayerCommand.ClearQueue -> clearQueue()
            is PlayerCommand.SetVolume -> setVolume(command.volume)
            is PlayerCommand.SetLoudnessPreference -> setLoudnessPreference(command.preference)
            is PlayerCommand.Reload -> reload()
            is PlayerCommand.Stop -> stop()
            is PlayerCommand.SetCrossfade -> setCrossfade(command.durationMs)
            is PlayerCommand.SetEqEnabled -> setEqEnabled(command.enabled)
        }
    }

    private fun loadTrack(track: dev.aurora.player.domain.models.MediaItem, playWhenReady: Boolean) {
        Log.d(TAG, "loadTrack: id=${track.id}, title=${track.title}, provider=${track.provider}, playWhenReady=$playWhenReady")
        targetPlayIntent = playWhenReady
        _state.update { current ->
            // A directly loaded track must exist in the queue. Without this, skipNext()
            // returns at its empty-queue guard when the track ends, so the player never
            // reaches Completed, history never records a completion, and no queue snapshot
            // is ever written.
            val existingIndex = current.queue.items.indexOfFirst { it.id == track.id }
            val queue = if (existingIndex >= 0) {
                // Already queued: select it rather than adding a duplicate.
                current.queue.copy(currentIndex = existingIndex)
            } else {
                // Load replaces the queue; AddToQueue is the command that appends.
                current.queue.copy(
                    items = listOf(track),
                    currentIndex = 0,
                    shuffledOrder = emptyList()
                )
            }
            current.copy(
                status = PlaybackStatus.Loading,
                currentTrack = track,
                queue = queue
            )
        }
        scope.launch {
            val uri = resolver.resolveUri(track.id)
            val loudnessData = resolver.resolveLoudness(track.id)
            Log.d(TAG, "loadTrack: resolved uri=${uri != null}, loudness=${loudnessData != null}")
            
            if (uri != null) {
                val capability = if (track.provider == dev.aurora.player.domain.models.ProviderKind.YOUTUBE) {
                    dev.aurora.player.domain.audio.LoudnessCapability.ProviderManaged
                } else {
                    dev.aurora.player.domain.audio.LoudnessCapability.Supported
                }
                
                val normalization = dev.aurora.player.domain.audio.LoudnessResolver.resolveGain(
                    preference = _state.value.loudnessPreference,
                    trackData = loudnessData,
                    capability = capability,
                    isAlbumContext = false
                )
                
                val linearGain = dev.aurora.player.domain.audio.LoudnessResolver.toLinearGain(normalization.actualGainDb)
                
                val capabilities = dev.aurora.player.domain.audio.ProviderAudioCapabilities(
                    provider = track.provider,
                    normalization = if (track.provider == dev.aurora.player.domain.models.ProviderKind.YOUTUBE) dev.aurora.player.domain.audio.CapabilityState.NOT_APPLICABLE else dev.aurora.player.domain.audio.CapabilityState.SUPPORTED,
                    crossfade = if (track.provider == dev.aurora.player.domain.models.ProviderKind.YOUTUBE) dev.aurora.player.domain.audio.CapabilityState.NOT_APPLICABLE else dev.aurora.player.domain.audio.CapabilityState.SUPPORTED,
                    eq = if (track.provider == dev.aurora.player.domain.models.ProviderKind.YOUTUBE) dev.aurora.player.domain.audio.CapabilityState.NOT_APPLICABLE else dev.aurora.player.domain.audio.CapabilityState.SUPPORTED,
                    limiter = if (track.provider == dev.aurora.player.domain.models.ProviderKind.YOUTUBE) dev.aurora.player.domain.audio.CapabilityState.NOT_APPLICABLE else dev.aurora.player.domain.audio.CapabilityState.SUPPORTED
                )
                
                _state.update { 
                    it.copy(
                        appliedNormalization = normalization,
                        providerCapabilities = capabilities
                    ) 
                }
                adapter.setAudioGain(linearGain)
                
                val crossfadeDuration = if (capabilities.crossfade == dev.aurora.player.domain.audio.CapabilityState.SUPPORTED) {
                    _state.value.crossfadeDurationMs
                } else 0L

                Log.d(TAG, "loadTrack: calling adapter.load(), crossfade=$crossfadeDuration")
                adapter.load(track, uri, playWhenReady, crossfadeDuration)
            } else {
                Log.e(TAG, "loadTrack: URI resolution failed for ${track.id}")
                _state.update { it.copy(status = PlaybackStatus.Error, lastError = Exception("URI not found")) }
            }
        }
    }

    private fun play() {
        val currentTrack = _state.value.currentTrack
        if (currentTrack != null && _state.value.status != PlaybackStatus.Idle) {
            targetPlayIntent = true
            adapter.play()
            // We wait for EngineEvent.Started to actually move to Playing
        } else {
            // Nothing to play, try next in queue?
            if (_state.value.queue.items.isNotEmpty()) {
                val nextIdx = if (_state.value.queue.currentIndex == -1) 0 else _state.value.queue.currentIndex
                val nextTrack = _state.value.queue.items[nextIdx]
                _state.update { it.copy(queue = it.queue.copy(currentIndex = nextIdx)) }
                loadTrack(nextTrack, playWhenReady = true)
            }
        }
    }

    private fun pause() {
        targetPlayIntent = false
        adapter.pause()
    }

    private fun seekTo(target: Long) {
        val currentDuration = _state.value.position.duration
        if (currentDuration != null && target > currentDuration) {
            adapter.seekTo(currentDuration)
        } else if (target < 0) {
            adapter.seekTo(0)
        } else {
            adapter.seekTo(target)
        }
    }

    private fun skipNext() {
        val q = _state.value.queue
        if (q.items.isEmpty()) return
        
        var nextIdx = q.currentIndex + 1
        
        if (q.shuffleMode == ShuffleMode.On) {
            val currentPos = q.shuffledOrder.indexOf(q.currentIndex)
            if (currentPos != -1 && currentPos + 1 < q.shuffledOrder.size) {
                nextIdx = q.shuffledOrder[currentPos + 1]
            } else {
                if (q.repeatMode == RepeatMode.All) {
                    nextIdx = if (q.shuffledOrder.isNotEmpty()) q.shuffledOrder[0] else 0
                } else {
                    targetPlayIntent = false
                    adapter.pause()
                    _state.update { it.copy(status = PlaybackStatus.Completed) }
                    return
                }
            }
        } else {
            if (nextIdx >= q.items.size) {
                if (q.repeatMode == RepeatMode.All) {
                    nextIdx = 0
                } else {
                    targetPlayIntent = false
                    adapter.pause()
                    _state.update { it.copy(status = PlaybackStatus.Completed) }
                    return
                }
            }
        }
        _state.update { it.copy(queue = it.queue.copy(currentIndex = nextIdx)) }
        loadTrack(q.items[nextIdx], playWhenReady = targetPlayIntent)
    }

    private fun skipPrevious() {
        val q = _state.value.queue
        if (q.items.isEmpty()) return
        
        if (_state.value.position.elapsed > 3000) {
            seekTo(0)
            return
        }

        var prevIdx = q.currentIndex - 1
        
        if (q.shuffleMode == ShuffleMode.On) {
            val currentPos = q.shuffledOrder.indexOf(q.currentIndex)
            if (currentPos > 0) {
                prevIdx = q.shuffledOrder[currentPos - 1]
            } else {
                if (q.repeatMode == RepeatMode.All) {
                    prevIdx = if (q.shuffledOrder.isNotEmpty()) q.shuffledOrder.last() else 0
                } else {
                    prevIdx = if (q.shuffledOrder.isNotEmpty()) q.shuffledOrder[0] else 0
                }
            }
        } else {
            if (prevIdx < 0) {
                if (q.repeatMode == RepeatMode.All) {
                    prevIdx = q.items.size - 1
                } else {
                    prevIdx = 0
                }
            }
        }
        _state.update { it.copy(queue = it.queue.copy(currentIndex = prevIdx)) }
        loadTrack(q.items[prevIdx], playWhenReady = targetPlayIntent)
    }

    private fun replay() {
        seekTo(0)
        play()
    }

    private fun setShuffle(mode: ShuffleMode) {
        _state.update {
            val q = it.queue
            val shuffled = if (mode == ShuffleMode.On) {
                generateShuffledOrder(q.items.size, q.currentIndex)
            } else {
                emptyList()
            }
            it.copy(queue = q.copy(shuffleMode = mode, shuffledOrder = shuffled))
        }
    }

    private fun generateShuffledOrder(size: Int, firstIndex: Int): List<Int> {
        if (size <= 0) return emptyList()
        val indices = (0 until size).toMutableList()
        if (firstIndex in indices) {
            indices.remove(firstIndex)
            indices.shuffle()
            indices.add(0, firstIndex)
        } else {
            indices.shuffle()
        }
        return indices
    }

    private fun setRepeat(mode: RepeatMode) {
        _state.update { it.copy(queue = it.queue.copy(repeatMode = mode)) }
    }

    private fun addToQueue(track: dev.aurora.player.domain.models.MediaItem) {
        _state.update { 
            val q = it.queue
            val newItems = q.items + track
            val newShuffled = if (q.shuffleMode == ShuffleMode.On) {
                q.shuffledOrder + (newItems.size - 1)
            } else emptyList()
            it.copy(queue = q.copy(items = newItems, shuffledOrder = newShuffled))
        }
    }

    private fun removeFromQueue(trackId: String) {
        _state.update {
            val q = it.queue
            val removeIdx = q.items.indexOfFirst { item -> item.id == trackId }
            if (removeIdx == -1) return@update it
            
            val updatedItems = q.items.toMutableList().apply { removeAt(removeIdx) }
            var updatedIdx = q.currentIndex
            if (updatedIdx == removeIdx) {
                if (updatedIdx >= updatedItems.size) updatedIdx = updatedItems.size - 1
            } else if (updatedIdx > removeIdx) {
                updatedIdx -= 1
            }
            
            val newShuffled = if (q.shuffleMode == ShuffleMode.On) {
                val s = q.shuffledOrder.toMutableList()
                s.remove(removeIdx)
                s.map { idx -> if (idx > removeIdx) idx - 1 else idx }
            } else emptyList()
            
            it.copy(queue = q.copy(items = updatedItems, currentIndex = updatedIdx, shuffledOrder = newShuffled))
        }
    }

    private fun moveInQueue(from: Int, to: Int) {
        _state.update {
            val q = it.queue
            val items = q.items.toMutableList()
            var currentIdx = q.currentIndex
            var newShuffled = q.shuffledOrder

            if (from in items.indices && to in items.indices && from != to) {
                val item = items.removeAt(from)
                items.add(to, item)

                if (currentIdx == from) {
                    currentIdx = to
                } else if (from < to && currentIdx in (from + 1)..to) {
                    currentIdx -= 1
                } else if (from > to && currentIdx in to until from) {
                    currentIdx += 1
                }

                if (q.shuffleMode == ShuffleMode.On) {
                    newShuffled = q.shuffledOrder.map { idx ->
                        when {
                            idx == from -> to
                            from < to && idx in (from + 1)..to -> idx - 1
                            from > to && idx in to until from -> idx + 1
                            else -> idx
                        }
                    }
                }
            }
            it.copy(queue = q.copy(items = items, currentIndex = currentIdx, shuffledOrder = newShuffled))
        }
    }

    private fun clearQueue() {
        _state.update { it.copy(queue = it.queue.copy(items = emptyList(), currentIndex = -1, shuffledOrder = emptyList())) }
    }

    private fun setVolume(volume: Float) {
        _state.update { it.copy(volume = volume) }
        adapter.setVolume(volume)
    }

    private fun setLoudnessPreference(preference: dev.aurora.player.domain.audio.LoudnessPreference) {
        _state.update { it.copy(loudnessPreference = preference) }
        reload()
    }

    private fun reload() {
        val currentTrack = _state.value.currentTrack
        if (currentTrack != null) {
            loadTrack(currentTrack, targetPlayIntent)
        }
    }

    private fun stop() {
        targetPlayIntent = false
        adapter.pause()
        _state.update { it.copy(status = PlaybackStatus.Idle, currentTrack = null) }
    }

    private fun setCrossfade(durationMs: Long) {
        _state.update { it.copy(crossfadeDurationMs = durationMs) }
    }

    private fun setEqEnabled(enabled: Boolean) {
        _state.update { it.copy(isEqEnabled = enabled) }
    }

    private fun handleEngineEvent(event: EngineEvent) {
        Log.d(TAG, "handleEngineEvent: $event")
        when (event) {
            is EngineEvent.Prepared -> {
                Log.d(TAG, "Engine PREPARED, targetPlayIntent=$targetPlayIntent")
                if (targetPlayIntent) {
                    adapter.play()
                } else {
                    _state.update { it.copy(status = PlaybackStatus.Ready) }
                }
            }
            is EngineEvent.Started -> {
                Log.d(TAG, "Engine STARTED -> PlaybackStatus.Playing")
                _state.update { it.copy(status = PlaybackStatus.Playing) }
            }
            is EngineEvent.Paused -> {
                Log.d(TAG, "Engine PAUSED -> PlaybackStatus.Paused")
                applySettledStatus(PlaybackStatus.Paused)
            }
            is EngineEvent.BufferingChanged -> {
                Log.d(TAG, "Engine BufferingChanged: isBuffering=${event.isBuffering}")
                if (event.isBuffering) {
                    _state.update { it.copy(status = PlaybackStatus.Buffering) }
                } else {
                    applySettledStatus(
                        if (targetPlayIntent) PlaybackStatus.Playing else PlaybackStatus.Paused
                    )
                }
            }
            is EngineEvent.PositionChanged -> {
                Log.v(TAG, "Position: elapsed=${event.elapsed}ms, duration=${event.duration}ms")
                _state.update {
                    it.copy(
                        position = PlaybackPosition(
                            elapsed = event.elapsed,
                            duration = event.duration,
                            buffered = event.buffered
                        )
                    )
                }
                
                // Proactive crossfade check
                if (targetPlayIntent && event.duration != null && event.duration > 0) {
                    val crossfadeMs = _state.value.crossfadeDurationMs
                    val remaining = event.duration - event.elapsed
                    
                    // Trigger skipNext slightly before the track ends (at crossfadeMs + 100ms threshold to ensure we catch it)
                    // We only want to do this once per track, so we check if we are very close to the threshold.
                    // This is a naive polling approach. In a real app we'd use a Handler/Timer scheduled precisely,
                    // but since PositionChanged emits every 200ms, this is acceptable for Phase 22 scaffold.
                    if (crossfadeMs > 0 && remaining <= crossfadeMs && remaining > crossfadeMs - 300) {
                        Log.d(TAG, "Triggering proactive crossfade. Remaining: $remaining, Crossfade: $crossfadeMs")
                        val q = _state.value.queue
                        if (q.repeatMode == RepeatMode.One) {
                            replay()
                        } else {
                            skipNext()
                        }
                    }
                }
            }
            is EngineEvent.SeekStarted -> {
                _state.update { it.copy(status = PlaybackStatus.Seeking) }
            }
            is EngineEvent.SeekCompleted -> {
                val status = if (targetPlayIntent) PlaybackStatus.Playing else PlaybackStatus.Paused
                _state.update { it.copy(status = status) }
            }
            is EngineEvent.TrackCompleted -> {
                Log.d(TAG, "Engine TrackCompleted")
                val q = _state.value.queue
                if (q.repeatMode == RepeatMode.One) {
                    replay()
                } else {
                    skipNext()
                }
            }
            is EngineEvent.Error -> {
                Log.e(TAG, "Engine ERROR: ${event.error}")
                _state.update { it.copy(status = PlaybackStatus.Error, lastError = event.error) }
            }
        }
    }

    /**
     * Applies a status that reflects the engine settling, unless playback already reached a
     * terminal state.
     *
     * Media3 emits BufferingChanged(false) - and, depending on the adapter, Paused -
     * immediately after TrackCompleted. Letting either overwrite Completed reset the status
     * about a millisecond after it was set, so nothing observing this conflated StateFlow
     * ever saw the completion, and finished tracks were never recorded in listening history.
     *
     * Started is deliberately not routed through here: it means playback genuinely resumed.
     */
    private fun applySettledStatus(status: PlaybackStatus) {
        _state.update {
            if (it.status == PlaybackStatus.Completed || it.status == PlaybackStatus.Error) {
                it
            } else {
                it.copy(status = status)
            }
        }
    }

    companion object {
        private const val TAG = "PlayerCoordinator"
    }
}
