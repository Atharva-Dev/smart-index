package com.kuriousdragon.smartindex.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import com.kuriousdragon.smartindex.core.model.PerfTrace

internal const val ITERATIONS = 20
internal const val STARTUP_ITERATIONS = 15
internal const val ROWS_TIMEOUT_MS = 10_000L
internal const val IDLE_MS = 1_000L

/** The `Parameterized` name of the search scenarios: `{0}_{1}_{2}` = text_tab_preset. */
internal const val PARAM_NAME = "{0}_{1}_{2}"

/** Compilation mode as shipped: the baseline profile is required (fails if none is installed). */
internal fun shippedCompilation() = CompilationMode.Partial(BaselineProfileMode.Require)

/**
 * The search measure step shared by S1, S2, S3 and S6 (LLD §3.6): setupBlock opens Search, clears, selects
 * tab/preset; measureBlock sets the text in one call, waits for rows or "No results", then idles 1 s so the
 * counts finish. With an empty keyword the date preset is the commit, so it is picked in the measure block.
 */
@OptIn(ExperimentalMetricApi::class)
internal fun MacrobenchmarkRule.measureSearch(q: Q) {
    val presetIsTheCommit = q.text.isEmpty() && q.presetTag != null
    measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(
            TraceSectionMetric(PerfTrace.SEARCH_FIRST_PAGE, TraceSectionMetric.Mode.First),
            TraceSectionMetric(PerfTrace.SEARCH_COUNTS, TraceSectionMetric.Mode.First),
            TraceSectionMetric(PerfTrace.STORE_PAGE, TraceSectionMetric.Mode.First),
        ),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        compilationMode = shippedCompilation(),
        setupBlock = {
            pressHome()
            startActivityAndWait()
            IndexReady.check(device)
            device.openSearch()
            device.clearQuery()
            device.pickTab(q.tabTag)
            if (presetIsTheCommit) {
                device.pickDatePreset(Tags.PRESET_ANY)
            } else if (q.presetTag != null) {
                device.pickDatePreset(q.presetTag)
            }
        },
    ) {
        device.setQuery(q.text)
        if (presetIsTheCommit) device.pickDatePreset(requireNotNull(q.presetTag))
        device.awaitRowsOrEmpty(ROWS_TIMEOUT_MS, what = "query '${q.text}' tab ${q.tabTag} date ${q.presetTag}")
        Thread.sleep(IDLE_MS)
    }
}
