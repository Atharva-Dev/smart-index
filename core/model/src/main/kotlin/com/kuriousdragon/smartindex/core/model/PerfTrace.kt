package com.kuriousdragon.smartindex.core.model

import android.os.Trace
import java.util.concurrent.atomic.AtomicInteger

/**
 * Trace sections measured by the Macrobenchmark (LLD — Test & benchmark harness §3.3). The names are a
 * contract: `:benchmark` matches them exactly with TraceSectionMetric. All sections are ASYNC
 * (Trace.beginAsyncSection), so begin and end may run on different threads and coroutines; [cookie]
 * separates overlapping instances of the same name. Never put user content in a name.
 */
object PerfTrace {
    /** UI (SIDX-13): search query committed (after debounce) → first frame showing its first rows or its empty state. */
    const val SEARCH_FIRST_PAGE = "SIDX:search:firstPage"

    /** UI (SIDX-13): same commit → per-tab counts applied to the UI state. Reported, not gated. */
    const val SEARCH_COUNTS = "SIDX:search:counts"

    /** UI (SIDX-13): day selected (Home start, ‹, ›, picker) → first frame showing that day's first rows or its empty state. */
    const val TIMELINE_FIRST_PAGE = "SIDX:timeline:firstPage"

    /** Store (SIDX-7 code, added by the harness ticket): one IndexPagingSource.load (key query + hydrate). */
    const val STORE_PAGE = "SIDX:store:page"

    /** Store (SIDX-7 code, added by the harness ticket): one RoomIndexQuery.counts(…) call. */
    const val STORE_COUNTS = "SIDX:store:counts"

    /** App (PerfMarkers, SIDX-15): an index run with mode FULL, from leaving IDLE to IDLE again. */
    const val INDEX_FULL = "SIDX:index:full"

    private val cookies = AtomicInteger(0)

    /** False on plain JVM unit tests (android.jar stubs throw); then every call is a no-op. */
    private val available: Boolean = try {
        Trace.isEnabled()
        true
    } catch (e: RuntimeException) {
        false
    }

    /** A process-unique cookie for one section instance. */
    fun nextCookie(): Int = cookies.incrementAndGet()

    fun begin(name: String, cookie: Int) {
        if (available) Trace.beginAsyncSection(name, cookie)
    }

    fun end(name: String, cookie: Int) {
        if (available) Trace.endAsyncSection(name, cookie)
    }

    /** Wraps [block] (may suspend if inlined into a suspend function) in one async section. */
    inline fun <T> traced(name: String, block: () -> T): T {
        val cookie = nextCookie()
        begin(name, cookie)
        try {
            return block()
        } finally {
            end(name, cookie)
        }
    }
}
