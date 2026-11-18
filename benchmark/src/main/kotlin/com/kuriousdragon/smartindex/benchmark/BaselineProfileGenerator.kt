package com.kuriousdragon.smartindex.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Collects the Home and Search baseline profile (LLD — Test & benchmark harness §3.7). Needs a seeded, indexed device. */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(packageName = PACKAGE_NAME, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
            IndexReady.waitIdle(device)

            // Home timeline: scroll, then one day back.
            val list = device.requireTag(Tags.LIST)
            list.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
            repeat(FLINGS) {
                list.fling(Direction.DOWN)
                device.waitForIdle()
            }
            device.requireTag(Tags.PREV_DAY).click()
            device.waitForIdle()

            // Search: three queries, waiting for rows each time.
            device.openSearch()
            QUERIES.forEach { query ->
                device.setQuery(query)
                device.awaitRowsOrEmpty(ROWS_TIMEOUT_MS, what = "query '$query'")
                device.waitForIdle()
            }
            device.pickTab(Tags.TAB_MESSAGE)
            device.waitForIdle()
            device.pickTab(Tags.TAB_ALL)
            device.waitForIdle()
            device.pickDatePreset(Tags.PRESET_LAST_7_DAYS)
            device.waitForIdle()

            device.pressBack()
            device.pressBack()
        }

    private companion object {
        val QUERIES = listOf("ra", "payment", "sea view")
        const val FLINGS = 2
        const val GESTURE_MARGIN_DIVISOR = 5
    }
}
