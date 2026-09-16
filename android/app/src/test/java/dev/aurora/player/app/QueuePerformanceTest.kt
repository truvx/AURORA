package dev.aurora.player.app

import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.domain.player.PlayerCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression budgets for queue operations.
 *
 * docs/PERFORMANCE_BUDGET.md sets 50 ms for a domain queue operation. These measure the pure
 * reducer with no Android, database, or IO involved, so what is timed is the algorithm.
 *
 * The thresholds are deliberately far above the numbers seen locally (single-digit
 * milliseconds): a timing assertion that sits close to the real value becomes a CI flake,
 * and a flaky budget gets deleted rather than fixed. These catch an operation going
 * quadratic, not a few milliseconds of noise.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QueuePerformanceTest {

    private companion object {
        const val BUDGET_MS = 50L
        const val LARGE_QUEUE = 1_000
    }

    private class SilentAdapter : PlayerAdapter {
        override val events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
        override fun load(track: MediaItem, uri: String, playWhenReady: Boolean, crossfadeDurationMs: Long) {}
        override fun play() {}
        override fun pause() {}
        override fun seekTo(positionMs: Long) {}
        override fun setVolume(volume: Float) {}
        override fun setAudioGain(linearGain: Float) {}
        override fun release() {}
    }

    private class InstantResolver : TrackResolver {
        override suspend fun resolveUri(trackId: String) = "file://$trackId"
        override suspend fun resolveLoudness(trackId: String): dev.aurora.player.domain.audio.TrackLoudnessData? = null
    }

    private fun track(index: Int) = MediaItem(
        id = "track_$index",
        provider = ProviderKind.LOCAL,
        title = "Track $index",
        artist = null,
        album = null,
        artworkUri = null
    )

    private fun coordinatorWithLargeQueue(scope: kotlinx.coroutines.CoroutineScope): PlayerCoordinator {
        val coordinator = PlayerCoordinator(SilentAdapter(), InstantResolver(), scope)
        repeat(LARGE_QUEUE) { coordinator.dispatch(PlayerCommand.AddToQueue(track(it))) }
        return coordinator
    }

    private inline fun measureMs(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }

    @Test
    fun `adding to a large queue stays within budget`() = runTest(UnconfinedTestDispatcher()) {
        val coordinator = coordinatorWithLargeQueue(backgroundScope)

        val elapsed = measureMs {
            coordinator.dispatch(PlayerCommand.AddToQueue(track(LARGE_QUEUE + 1)))
        }

        assertTrue("add took ${elapsed}ms, budget ${BUDGET_MS}ms", elapsed < BUDGET_MS)
        assertEquals(LARGE_QUEUE + 1, coordinator.state.value.queue.items.size)
    }

    @Test
    fun `removing from a large queue stays within budget`() = runTest(UnconfinedTestDispatcher()) {
        val coordinator = coordinatorWithLargeQueue(backgroundScope)

        val elapsed = measureMs {
            coordinator.dispatch(PlayerCommand.RemoveFromQueue("track_500"))
        }

        assertTrue("remove took ${elapsed}ms, budget ${BUDGET_MS}ms", elapsed < BUDGET_MS)
        assertEquals(LARGE_QUEUE - 1, coordinator.state.value.queue.items.size)
    }

    @Test
    fun `reordering a large queue stays within budget`() = runTest(UnconfinedTestDispatcher()) {
        val coordinator = coordinatorWithLargeQueue(backgroundScope)

        val elapsed = measureMs {
            coordinator.dispatch(PlayerCommand.MoveInQueue(0, LARGE_QUEUE - 1))
        }

        assertTrue("reorder took ${elapsed}ms, budget ${BUDGET_MS}ms", elapsed < BUDGET_MS)
    }

    @Test
    fun `building a large queue does not degrade per operation`() = runTest(UnconfinedTestDispatcher()) {
        val coordinator = PlayerCoordinator(SilentAdapter(), InstantResolver(), backgroundScope)

        // Time the first and last hundred additions. A quadratic implementation - copying or
        // re-scanning the whole queue per add - shows up as the tail costing far more than
        // the head, which a single-operation budget would miss entirely.
        val head = measureMs { repeat(100) { coordinator.dispatch(PlayerCommand.AddToQueue(track(it))) } }
        repeat(LARGE_QUEUE) { coordinator.dispatch(PlayerCommand.AddToQueue(track(it + 100))) }
        val tail = measureMs {
            repeat(100) { coordinator.dispatch(PlayerCommand.AddToQueue(track(it + 2_000))) }
        }

        // Generous: only a severe scaling regression trips this, not ordinary variance.
        assertTrue(
            "queue additions degraded from ${head}ms to ${tail}ms as the queue grew",
            tail <= (head + 1) * 20
        )
    }

    @Test
    fun `clearing a large queue stays within budget`() = runTest(UnconfinedTestDispatcher()) {
        val coordinator = coordinatorWithLargeQueue(backgroundScope)

        val elapsed = measureMs { coordinator.dispatch(PlayerCommand.ClearQueue) }

        assertTrue("clear took ${elapsed}ms, budget ${BUDGET_MS}ms", elapsed < BUDGET_MS)
        assertTrue(coordinator.state.value.queue.items.isEmpty())
    }
}
