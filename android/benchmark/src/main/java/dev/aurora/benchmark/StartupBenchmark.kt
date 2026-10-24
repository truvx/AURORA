package dev.aurora.benchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures cold start against docs/PERFORMANCE_BUDGET.md.
 *
 * Nothing measured startup before this, so the budget was a number in a document with no
 * way to tell whether the app met it or had regressed.
 *
 * Requires a physical device. androidx.benchmark refuses to run on an emulator, and that
 * refusal is deliberately not suppressed: emulator timings do not reflect real hardware, and
 * a number that looks like a measurement but is not is worse than no number.
 *
 * Run with:  ./gradlew :benchmark:connectedBenchmarkAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = "dev.aurora.player.benchmark",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD
    ) {
        pressHome()
        startActivityAndWait()
    }
}
