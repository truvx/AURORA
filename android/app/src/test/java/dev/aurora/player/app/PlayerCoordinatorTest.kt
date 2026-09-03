package dev.aurora.player.app

import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.domain.player.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerCoordinatorTest {

    private lateinit var coordinator: PlayerCoordinator
    private lateinit var adapterEvents: MutableSharedFlow<EngineEvent>

    private val fakeTrack = MediaItem(
        id = "track1",
        provider = ProviderKind.LOCAL,
        title = "Test Track",
        artist = null,
        album = null,
        artworkUri = null
    )

    private val fakeAdapter = object : PlayerAdapter {
        override val events = MutableSharedFlow<EngineEvent>(replay = 1, extraBufferCapacity = 64)
        var loadedTrack: MediaItem? = null
        var isPlaying = false
        var seekPosition = -1L

        override fun load(track: MediaItem, uri: String) {
            loadedTrack = track
            events.tryEmit(EngineEvent.Prepared)
        }
        override fun play() {
            isPlaying = true
            events.tryEmit(EngineEvent.Started)
        }
        override fun pause() {
            isPlaying = false
            events.tryEmit(EngineEvent.Paused)
        }
        override fun seekTo(positionMs: Long) {
            seekPosition = positionMs
            events.tryEmit(EngineEvent.SeekStarted)
            events.tryEmit(EngineEvent.SeekCompleted)
        }
        override fun setVolume(volume: Float) {}
        override fun setAudioGain(linearGain: Float) {}
        override fun release() {}
    }

    private val fakeResolver = object : TrackResolver {
        override suspend fun resolveUri(trackId: String): String? {
            return if (trackId == "notfound") null else "content://test/$trackId"
        }
        override suspend fun resolveLoudness(trackId: String): dev.aurora.player.domain.audio.TrackLoudnessData? = null
    }

    @Before
    fun setup() {
        adapterEvents = fakeAdapter.events
    }

    @Test
    fun testLoadTrackUpdatesStateToReady() = runTest(UnconfinedTestDispatcher()) {
        coordinator = PlayerCoordinator(fakeAdapter, fakeResolver, backgroundScope)
        coordinator.dispatch(PlayerCommand.Load(fakeTrack))
        testScheduler.advanceUntilIdle()

        val state = coordinator.state.value
        assertEquals(PlaybackStatus.Ready, state.status)
        assertEquals(fakeTrack, state.currentTrack)
    }

    @Test
    fun testPlayCommandStartsPlayback() = runTest(UnconfinedTestDispatcher()) {
        coordinator = PlayerCoordinator(fakeAdapter, fakeResolver, backgroundScope)
        coordinator.dispatch(PlayerCommand.Load(fakeTrack))
        testScheduler.advanceUntilIdle()
        
        coordinator.dispatch(PlayerCommand.Play)
        testScheduler.advanceUntilIdle()

        val state = coordinator.state.value
        assertEquals(PlaybackStatus.Playing, state.status)
        assertEquals(true, fakeAdapter.isPlaying)
    }

    @Test
    fun testPauseCommandStopsPlayback() = runTest(UnconfinedTestDispatcher()) {
        coordinator = PlayerCoordinator(fakeAdapter, fakeResolver, backgroundScope)
        coordinator.dispatch(PlayerCommand.Load(fakeTrack))
        coordinator.dispatch(PlayerCommand.Play)
        testScheduler.advanceUntilIdle()

        coordinator.dispatch(PlayerCommand.Pause)
        testScheduler.advanceUntilIdle()

        val state = coordinator.state.value
        assertEquals(PlaybackStatus.Paused, state.status)
        assertEquals(false, fakeAdapter.isPlaying)
    }

    @Test
    fun testQueueTraversalSkipNext() = runTest(UnconfinedTestDispatcher()) {
        coordinator = PlayerCoordinator(fakeAdapter, fakeResolver, backgroundScope)
        val track2 = fakeTrack.copy(id = "track2")
        coordinator.dispatch(PlayerCommand.AddToQueue(fakeTrack))
        coordinator.dispatch(PlayerCommand.AddToQueue(track2))
        
        // Next will play track1
        coordinator.dispatch(PlayerCommand.Play)
        testScheduler.advanceUntilIdle()
        assertEquals("track1", coordinator.state.value.currentTrack?.id)

        coordinator.dispatch(PlayerCommand.SkipNext)
        testScheduler.advanceUntilIdle()
        assertEquals("track2", coordinator.state.value.currentTrack?.id)
        assertEquals(1, coordinator.state.value.queue.currentIndex)
    }
}
