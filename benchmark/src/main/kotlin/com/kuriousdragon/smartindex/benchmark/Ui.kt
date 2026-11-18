package com.kuriousdragon.smartindex.benchmark

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** The app under test. */
const val PACKAGE_NAME = "com.kuriousdragon.smartindex"

/**
 * Test tags of LLD — Test & benchmark harness §3.3. The app sets `testTagsAsResourceId`, so a tag is the
 * resource id UI Automator sees; [By.res] with one argument matches it exactly.
 */
object Tags {
    const val SEARCH_BAR = "sidx_search_bar"
    const val SEARCH_FIELD = "sidx_search_field"
    const val SEARCH_CLEAR = "sidx_search_clear"
    const val LIST = "sidx_list"
    const val ROW = "sidx_row"
    const val NO_RESULTS = "sidx_no_results"
    const val EMPTY_DAY = "sidx_empty_day"
    const val DATE_CHIP = "sidx_date_chip"
    const val PREV_DAY = "sidx_prev_day"
    const val INDEXING_BANNER = "sidx_indexing_banner"
    const val TAB_ALL = "sidx_tab_ALL"
    const val TAB_MESSAGE = "sidx_tab_MESSAGE"
    const val TAB_CALL = "sidx_tab_CALL"
    const val PRESET_ANY = "sidx_date_preset_ANY"
    const val PRESET_LAST_7_DAYS = "sidx_date_preset_LAST_7_DAYS"
    const val PRESET_LAST_30_DAYS = "sidx_date_preset_LAST_30_DAYS"
}

private const val FIND_TIMEOUT_MS = 5_000L
private const val POLL_MS = 100L
private const val MAX_TAB_SWIPES = 4
private const val TAB_SWIPE_STEPS = 20
private const val TAB_SWIPE_IDLE_MS = 300L
private val ANY_TAB = Pattern.compile("sidx_tab_(ALL|PHOTO|VIDEO|DOCUMENT|OTHER_FILE|CALL|RECORDING|MESSAGE)")

/** Instrumentation runner argument, or null. Pass with `-Pandroid.testInstrumentationRunnerArguments.<name>=<v>`. */
fun runnerArg(name: String): String? = InstrumentationRegistry.getArguments().getString(name)

/** Finds the node tagged [tag]; fails with a clear message (never hangs) when it does not appear. */
fun UiDevice.requireTag(tag: String, timeoutMs: Long = FIND_TIMEOUT_MS): UiObject2 =
    wait(Until.findObject(By.res(tag)), timeoutMs)
        ?: throw AssertionError(
            "Test tag '$tag' not found within $timeoutMs ms - is the UI contract (LLD §3.3) implemented?",
        )

/** From Home, taps the idle search bar; no-op when Search is already open. */
fun UiDevice.openSearch() {
    if (!hasObject(By.res(Tags.SEARCH_FIELD))) requireTag(Tags.SEARCH_BAR).click()
    requireTag(Tags.SEARCH_FIELD)
}

/** Taps the clear button when it is shown. */
fun UiDevice.clearQuery() {
    findObject(By.res(Tags.SEARCH_CLEAR))?.let {
        it.click()
        wait(Until.gone(By.res(Tags.SEARCH_CLEAR)), FIND_TIMEOUT_MS)
    }
}

/** Sets the whole query in one `UiObject2.text =` call (no per-character typing). */
fun UiDevice.setQuery(text: String) {
    requireTag(Tags.SEARCH_FIELD).text = text
}

/**
 * Selects the type tab with test tag [tag], e.g. `sidx_tab_MESSAGE`. The tab row scrolls horizontally, so tabs
 * off screen are not in the accessibility tree: scroll the row towards its end, then back towards its start,
 * until the tab shows up.
 */
fun UiDevice.pickTab(tag: String) {
    repeat(MAX_TAB_SWIPES) {
        findObject(By.res(tag))?.let {
            it.click()
            return
        }
        swipeTabRow(towardsEnd = true)
    }
    repeat(MAX_TAB_SWIPES * 2) {
        findObject(By.res(tag))?.let {
            it.click()
            return
        }
        swipeTabRow(towardsEnd = false)
    }
    requireTag(tag).click() // final try; throws the clear AssertionError if still missing
}

/** Swipes along the tab row's own y so that a vertical list is never scrolled. */
private fun UiDevice.swipeTabRow(towardsEnd: Boolean) {
    val tab = findObject(By.res(ANY_TAB)) ?: requireTag(Tags.TAB_ALL)
    val y = tab.visibleCenter.y
    val from = (displayWidth * if (towardsEnd) 0.85 else 0.15).toInt()
    val to = (displayWidth * if (towardsEnd) 0.15 else 0.85).toInt()
    swipe(from, y, to, y, TAB_SWIPE_STEPS)
    waitForIdle(TAB_SWIPE_IDLE_MS)
}

/** Opens the date filter sheet and picks the preset with test tag [tag], e.g. `sidx_date_preset_LAST_30_DAYS`. */
fun UiDevice.pickDatePreset(tag: String) {
    requireTag(Tags.DATE_CHIP).click()
    requireTag(tag).click()
    requireTag(Tags.DATE_CHIP)
}

/**
 * Waits up to [timeoutMs] for a result row or one of [emptyTags] (the "No results" state for Search, the
 * empty-day state for the timeline). Fails with an [AssertionError] naming [what] on timeout.
 */
fun UiDevice.awaitRowsOrEmpty(
    timeoutMs: Long,
    emptyTags: List<String> = listOf(Tags.NO_RESULTS),
    what: String = "the list",
) {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    while (SystemClock.elapsedRealtime() < deadline) {
        if (hasObject(By.res(Tags.ROW)) || emptyTags.any { hasObject(By.res(it)) }) return
        SystemClock.sleep(POLL_MS)
    }
    throw AssertionError(
        "No '${Tags.ROW}' and none of $emptyTags within $timeoutMs ms for $what - a tag is missing or the list never loaded",
    )
}
