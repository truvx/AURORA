package dev.aurora.player.data.player

import dev.aurora.player.app.EngineEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the bridge-to-event translation. These methods are called by name from JavaScript
 * inside the IFrame, so nothing on the Kotlin side references them and neither the compiler
 * nor R8 can verify they still line up with the page.
 *
 * The adapter is constructed without a WebView: `initializeWebView` is a separate call, so
 * the event contract is testable off-device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class YouTubePlayerAdapterTest {

    @Test
    fun `position updates report an unknown buffered amount`() = runTest(UnconfinedTestDispatcher()) {
        val adapter = YouTubePlayerAdapter()
        val received = mutableListOf<EngineEvent>()
        val collector = launch { adapter.events.toList(received) }

        adapter.onPositionChanged(5_000L, 180_000L)
        collector.cancel()

        val position = received.filterIsInstance<EngineEvent.PositionChanged>().single()
        assertEquals(5_000L, position.elapsed)
        assertEquals(180_000L, position.duration)
        // YouTube exposes no buffered figure. Reporting one would be inventing it, and the
        // null is what identified these events when idle ticks were overwriting local
        // playback position.
        assertNull(position.buffered)
    }

    @Test
    fun `an unknown duration stays unknown`() = runTest(UnconfinedTestDispatcher()) {
        val adapter = YouTubePlayerAdapter()
        val received = mutableListOf<EngineEvent>()
        val collector = launch { adapter.events.toList(received) }

        adapter.onPositionChanged(1_000L, null)
        collector.cancel()

        assertNull(received.filterIsInstance<EngineEvent.PositionChanged>().single().duration)
    }

    @Test
    fun `pausing and completing emit distinct events`() = runTest(UnconfinedTestDispatcher()) {
        val adapter = YouTubePlayerAdapter()
        val received = mutableListOf<EngineEvent>()
        val collector = launch { adapter.events.toList(received) }

        adapter.onPaused()
        adapter.onTrackCompleted()
        collector.cancel()

        assertTrue(received.any { it is EngineEvent.Paused })
        assertTrue(received.any { it is EngineEvent.TrackCompleted })
    }

    @Test
    fun `buffering transitions are reported in both directions`() = runTest(UnconfinedTestDispatcher()) {
        val adapter = YouTubePlayerAdapter()
        val received = mutableListOf<EngineEvent>()
        val collector = launch { adapter.events.toList(received) }

        adapter.onBuffering(true)
        adapter.onBuffering(false)
        collector.cancel()

        assertEquals(
            listOf(true, false),
            received.filterIsInstance<EngineEvent.BufferingChanged>().map { it.isBuffering }
        )
    }

    @Test
    fun `player errors surface as engine errors rather than being swallowed`() =
        runTest(UnconfinedTestDispatcher()) {
            val adapter = YouTubePlayerAdapter()
            val received = mutableListOf<EngineEvent>()
            val collector = launch { adapter.events.toList(received) }

            // A provider limitation must reach the coordinator so the UI can say so, rather
            // than leaving a player that silently never advances.
            adapter.onError(IllegalStateException("Video unavailable"))
            collector.cancel()

            val error = received.filterIsInstance<EngineEvent.Error>().single()
            assertEquals("Video unavailable", error.error.message)
        }
}
