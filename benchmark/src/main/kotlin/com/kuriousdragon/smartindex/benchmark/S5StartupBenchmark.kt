package com.kuriousdragon.smartindex.benchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import com.kuriousdragon.smartindex.core.model.PerfTrace
import org.junit.Rule
import org.junit.Test

/**
 * S5: cold and warm start to Home today (LLD — Test & benchmark harness §3.6). TTID and TTFD (via
 * `ReportDrawnWhen`) are reported; the timeline first page is gated.
 */
class S5StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @OptIn(ExperimentalMetricApi::class)
    private val metrics: List<Metric> =
        listOf(
            StartupTimingMetric(),
            TraceSectionMetric(PerfTrace.TIMELINE_FIRST_PAGE, TraceSectionMetric.Mode.First),
        )

    @Test
    fun coldStart() = start(StartupMode.COLD)

    @Test
    fun warmStart() = start(StartupMode.WARM)

    private fun start(mode: StartupMode) =
        rule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = metrics,
            iterations = STARTUP_ITERATIONS,
            startupMode = mode,
            compilationMode = shippedCompilation(),
            setupBlock = { prepare(mode) },
        ) {
            pressHome()
            startActivityAndWait()
        }

    /** Launches once so the index precondition is checked, then returns to the state the mode needs. */
    private fun MacrobenchmarkScope.prepare(mode: StartupMode) {
        if (IndexReady.required) {
            startActivityAndWait()
            IndexReady.check(device)
            if (mode == StartupMode.COLD) killProcess()
        }
        pressHome()
    }
}
