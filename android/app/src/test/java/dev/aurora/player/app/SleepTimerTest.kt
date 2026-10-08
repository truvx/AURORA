package dev.aurora.player.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {

    private fun TestScope.timer(onPause: () -> Unit) =
        SleepTimer(backgroundScope, onPause, clock = { testScheduler.currentTime })

    @Test
    fun pausesOnceWhenTheTimeIsUp() = runTest {
        var pauses = 0
        val timer = timer { pauses++ }

        timer.start(minutes = 15)
        assertEquals(15 * 60_000L, timer.endsAt.value)

        advanceTimeBy(15 * 60_000L - 1)
        assertEquals(0, pauses)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, pauses)
        assertNull(timer.endsAt.value)
    }

    @Test
    fun cancellingMeansNoPause() = runTest {
        var pauses = 0
        val timer = timer { pauses++ }

        timer.start(minutes = 15)
        timer.cancel()
        advanceTimeBy(60 * 60_000L)

        assertEquals(0, pauses)
        assertNull(timer.endsAt.value)
    }

    @Test
    fun restartingReplacesTheOldDeadline() = runTest {
        var pauses = 0
        val timer = timer { pauses++ }

        timer.start(minutes = 15)
        timer.start(minutes = 30)
        advanceTimeBy(20 * 60_000L)
        assertEquals(0, pauses)

        advanceTimeBy(11 * 60_000L)
        assertEquals(1, pauses)
    }
}
