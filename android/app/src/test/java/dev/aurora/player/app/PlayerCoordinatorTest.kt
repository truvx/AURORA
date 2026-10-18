package dev.aurora.player.app

import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.domain.player.*
import dev.aurora.player.domain.player.providers.PlaybackSourceProvider
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

        override fun load(source: dev.aurora.player.domain.player.PlaybackSource, track: MediaItem, playWhenReady: Boolean, crossfadeDurationMs: Long) {
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
        override val sourceProviders: List<PlaybackSourceProvider> = emptyList()
        override suspend fun resolveSource(track: dev.aurora.player.domain.models.MediaItem, preferredQuality: dev.aurora.player.domain.audio.RequestedQuality): dev.aurora.player.domain.player.PlaybackSource? {
            return if (track.id == "notfound") null else dev.aurora.player.domain.player.PlaybackSource("content://test/${track.id}")
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

    @Test
    fun `crossfade hands over once per track, not once per position tick`() =
        runTest(UnconfinedTestDispatcher()) {
            /*
             * Positions arrive every 200ms and the crossfade window was 300ms wide, so two
             * ticks landed in it whenever the first fell in its leading 100ms: skipNext ran
             * twice, the queue advanced two places, and a track was never played.
             */
            coordinator = PlayerCoordinator(fakeAdapter, fakeResolver, backgroundScope)
            val a = trackWithId("a")
            val b = trackWithId("b")
            val c = trackWithId("c")
            coordinator.dispatch(PlayerCommand.AddToQueue(a))
            coordinator.dispatch(PlayerCommand.AddToQueue(b))
            coordinator.dispatch(PlayerCommand.AddToQueue(c))
            coordinator.dispatch(PlayerCommand.Load(a))
            coordinator.dispatch(PlayerCommand.Play)
            coordinator.dispatch(PlayerCommand.SetCrossfade(2_000L))
            testScheduler.advanceUntilIdle()

            // Two ticks inside one 2s crossfade window, 200ms apart, as the engine emits them.
            adapterEvents.emit(EngineEvent.PositionChanged(elapsed = 8_100L, duration = 10_000L, buffered = null))
            testScheduler.advanceUntilIdle()
            adapterEvents.emit(EngineEvent.PositionChanged(elapsed = 8_300L, duration = 10_000L, buffered = null))
            testScheduler.advanceUntilIdle()

            // One advance, so the track after the next one is still ahead of us.
            assertEquals(b.id, coordinator.state.value.currentTrack?.id)
        }

    @Test
    fun `playing from a list queues the list, starting where it was tapped`() =
        runTest(UnconfinedTestDispatcher()) {
            // Every entry point loaded a single track, so a fresh play left a one-item
            // queue and next and previous had nowhere to go.
            coordinator = PlayerCoordinator(fakeAdapter, fakeResolver, backgroundScope)
            val list = listOf(trackWithId("a"), trackWithId("b"), trackWithId("c"))

            coordinator.dispatch(PlayerCommand.PlayList(list, startIndex = 1))
            testScheduler.advanceUntilIdle()

            val state = coordinator.state.value
            assertEquals(listOf("a", "b", "c"), state.queue.items.map { it.id })
            assertEquals(1, state.queue.currentIndex)
            assertEquals("b", state.currentTrack?.id)
        }

    @Test
    fun `a list replaces what was queued, because a list is a new context`() =
        runTest(UnconfinedTestDispatcher()) {
            coordinator = PlayerCoordinator(fakeAdapter, fakeResolver, backgroundScope)
            coordinator.dispatch(PlayerCommand.AddToQueue(trackWithId("old")))
            testScheduler.advanceUntilIdle()

            coordinator.dispatch(
                PlayerCommand.PlayList(listOf(trackWithId("a"), trackWithId("b")), startIndex = 0)
            )
            testScheduler.advanceUntilIdle()

            assertEquals(listOf("a", "b"), coordinator.state.value.queue.items.map { it.id })
        }

    @Test
    fun `an out-of-range start plays the list from the beginning`() =
        runTest(UnconfinedTestDispatcher()) {
            // A stale index means the caller's idea of the list is old. Clamping to the
            // end would play one track and stop.
            coordinator = PlayerCoordinator(fakeAdapter, fakeResolver, backgroundScope)

            coordinator.dispatch(
                PlayerCommand.PlayList(listOf(trackWithId("a"), trackWithId("b")), startIndex = 99)
            )
            testScheduler.advanceUntilIdle()

            assertEquals("a", coordinator.state.value.currentTrack?.id)
        }

    @Test
    fun `next at the end of the queue stops, or wraps when repeat is all`() =
        runTest(UnconfinedTestDispatcher()) {
            coordinator = PlayerCoordinator(fakeAdapter, fakeResolver, backgroundScope)
            val list = listOf(trackWithId("a"), trackWithId("b"))

            coordinator.dispatch(PlayerCommand.PlayList(list, startIndex = 1))
            coordinator.dispatch(PlayerCommand.SkipNext)
            testScheduler.advanceUntilIdle()
            assertEquals(PlaybackStatus.Completed, coordinator.state.value.status)

            coordinator.dispatch(PlayerCommand.PlayList(list, startIndex = 1))
            coordinator.dispatch(PlayerCommand.SetRepeat(RepeatMode.All))
            coordinator.dispatch(PlayerCommand.SkipNext)
            testScheduler.advanceUntilIdle()
            assertEquals("a", coordinator.state.value.currentTrack?.id)
        }

    @Test
    fun `previous restarts the track once past three seconds, and steps back before it`() =
        runTest(UnconfinedTestDispatcher()) {
            coordinator = PlayerCoordinator(fakeAdapter, fakeResolver, backgroundScope)
            val list = listOf(trackWithId("a"), trackWithId("b"))

            coordinator.dispatch(PlayerCommand.PlayList(list, startIndex = 1))
            testScheduler.advanceUntilIdle()
            adapterEvents.emit(EngineEvent.PositionChanged(elapsed = 1_000L, duration = 200_000L, buffered = null))
            testScheduler.advanceUntilIdle()

            coordinator.dispatch(PlayerCommand.SkipPrevious)
            testScheduler.advanceUntilIdle()
            assertEquals("a", coordinator.state.value.currentTrack?.id)

            coordinator.dispatch(PlayerCommand.PlayList(list, startIndex = 1))
            testScheduler.advanceUntilIdle()
            adapterEvents.emit(EngineEvent.PositionChanged(elapsed = 9_000L, duration = 200_000L, buffered = null))
            testScheduler.advanceUntilIdle()

            coordinator.dispatch(PlayerCommand.SkipPrevious)
            testScheduler.advanceUntilIdle()
            // Restarted, not moved.
            assertEquals("b", coordinator.state.value.currentTrack?.id)
        }

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
