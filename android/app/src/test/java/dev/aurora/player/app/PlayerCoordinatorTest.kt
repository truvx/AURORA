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

        override fun load(track: MediaItem, uri: String, playWhenReady: Boolean, crossfadeDurationMs: Long) {
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
    @Test
    fun testCapabilitiesAndCrossfade() = runTest(UnconfinedTestDispatcher()) {
        coordinator = PlayerCoordinator(fakeAdapter, fakeResolver, backgroundScope)
        
        // Dispatch set crossfade
        coordinator.dispatch(PlayerCommand.SetCrossfade(2000L))
        testScheduler.advanceUntilIdle()
        
        // Load local track
        coordinator.dispatch(PlayerCommand.Load(fakeTrack))
        testScheduler.advanceUntilIdle()
        
        val state = coordinator.state.value
        assertEquals(2000L, state.crossfadeDurationMs)
        assertEquals(dev.aurora.player.domain.audio.CapabilityState.SUPPORTED, state.providerCapabilities?.crossfade)
    }

    // --- queue placement -----------------------------------------------------------
    //
    // loadTrack set currentTrack without touching the queue, so skipNext() returned at its
    // `if (q.items.isEmpty())` guard. PlaybackStatus.Completed was never reached, history
    // never recorded a COMPLETE, and no queue snapshot was ever written.

    private fun trackWithId(id: String) = fakeTrack.copy(id = id, title = "Track $id")

    @Test
    fun `loading a track puts it in the queue`() = runTest(UnconfinedTestDispatcher()) {
        coordinator = PlayerCoordinator(fakeAdapter, fakeResolver, backgroundScope)
        coordinator.dispatch(PlayerCommand.Load(trackWithId("local_1")))
        testScheduler.advanceUntilIdle()

        val queue = coordinator.state.value.queue
        assertEquals(listOf("local_1"), queue.items.map { it.id })
        assertEquals(0, queue.currentIndex)
    }

    @Test
    fun `loading a second track replaces the single-track queue rather than growing it`() =
        runTest(UnconfinedTestDispatcher()) {
            coordinator = PlayerCoordinator(fakeAdapter, fakeResolver, backgroundScope)
            coordinator.dispatch(PlayerCommand.Load(trackWithId("local_1")))
            testScheduler.advanceUntilIdle()
            coordinator.dispatch(PlayerCommand.Load(trackWithId("local_2")))
            testScheduler.advanceUntilIdle()

            val queue = coordinator.state.value.queue
            assertEquals(listOf("local_2"), queue.items.map { it.id })
            assertEquals(0, queue.currentIndex)
        }

    @Test
    fun `loading a track already queued selects it instead of duplicating it`() =
        runTest(UnconfinedTestDispatcher()) {
            coordinator = PlayerCoordinator(fakeAdapter, fakeResolver, backgroundScope)
            coordinator.dispatch(PlayerCommand.AddToQueue(trackWithId("local_1")))
            coordinator.dispatch(PlayerCommand.AddToQueue(trackWithId("local_2")))
            coordinator.dispatch(PlayerCommand.Load(trackWithId("local_2")))
            testScheduler.advanceUntilIdle()

            val queue = coordinator.state.value.queue
            assertEquals(listOf("local_1", "local_2"), queue.items.map { it.id })
            assertEquals(1, queue.currentIndex)
        }

    @Test
    fun `a finished track stays Completed when buffering settles afterwards`() =
        runTest(UnconfinedTestDispatcher()) {
            coordinator = PlayerCoordinator(fakeAdapter, fakeResolver, backgroundScope)
            coordinator.dispatch(PlayerCommand.Load(trackWithId("local_1")))
            testScheduler.advanceUntilIdle()

            // Media3 emits TrackCompleted and then BufferingChanged(false) on STATE_ENDED.
            // The second must not reset a terminal status back to Paused: state is a
            // conflated flow, so anything observing it would never see the completion.
            adapterEvents.emit(EngineEvent.TrackCompleted)
            testScheduler.advanceUntilIdle()
            adapterEvents.emit(EngineEvent.BufferingChanged(false))
            testScheduler.advanceUntilIdle()

            assertEquals(PlaybackStatus.Completed, coordinator.state.value.status)
        }
}
