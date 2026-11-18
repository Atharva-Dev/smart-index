package com.kuriousdragon.smartindex.benchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.uiautomator.Direction
import com.kuriousdragon.smartindex.core.model.PerfTrace
import org.junit.Rule
import org.junit.Test

/** S4: open the busy day, and scroll it (LLD — Test & benchmark harness §3.6). */
class S4TimelineBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Runner argument `sidx.busyDaysBack` (default 1 = the seeder's busy day when run on the seed day). */
    private val busyDaysBack: Int = runnerArg("sidx.busyDaysBack")?.toIntOrNull() ?: 1

    private fun MacrobenchmarkScope.goBack() {
        repeat(busyDaysBack) { device.requireTag(Tags.PREV_DAY).click() }
    }

    private fun MacrobenchmarkScope.awaitDay() =
        device.awaitRowsOrEmpty(ROWS_TIMEOUT_MS, listOf(Tags.EMPTY_DAY), "the busy day ($busyDaysBack days back)")

    // LLD §3.6 says Mode.Max, which androidx.benchmark 1.5.0 does not have. Mode.Max equals Last when
    // sidx.busyDaysBack = 1 (one section per iteration) and is the conservative choice for more taps.
    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun openBusyDay() =
        rule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(TraceSectionMetric(PerfTrace.TIMELINE_FIRST_PAGE, TraceSectionMetric.Mode.Max)),
            iterations = ITERATIONS,
            startupMode = StartupMode.WARM,
            compilationMode = shippedCompilation(),
            setupBlock = {
                pressHome()
                startActivityAndWait()
                IndexReady.check(device)
                device.requireTag(Tags.PREV_DAY)
            },
        ) {
            goBack()
            awaitDay()
            Thread.sleep(IDLE_MS)
        }

    @Test
    fun scrollBusyDay() =
        rule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            iterations = ITERATIONS,
            startupMode = StartupMode.WARM,
            compilationMode = shippedCompilation(),
            setupBlock = {
                pressHome()
                startActivityAndWait()
                IndexReady.check(device)
                goBack()
                awaitDay()
            },
        ) {
            val list = device.requireTag(Tags.LIST)
            list.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
            repeat(FLINGS) {
                list.fling(Direction.DOWN)
                device.waitForIdle()
            }
        }

    private companion object {
        const val FLINGS = 5
        const val GESTURE_MARGIN_DIVISOR = 5
    }
}
