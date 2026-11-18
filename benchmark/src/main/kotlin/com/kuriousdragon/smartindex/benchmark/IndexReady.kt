package com.kuriousdragon.smartindex.benchmark

import android.os.SystemClock
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice

/** Precondition of every scenario: the phone is indexed and idle (LLD — Test & benchmark harness §3.6). */
object IndexReady {
    private const val ABSENT_FOR_MS = 3_000L
    private const val MAX_WAIT_MS = 10 * 60_000L
    private const val POLL_MS = 250L
    private const val ALLOW_INDEXING_ARG = "sidx.allowIndexing"

    /** True unless the runner argument `sidx.allowIndexing=true` is set. */
    val required: Boolean get() = runnerArg(ALLOW_INDEXING_ARG) != "true"

    /** Waits for the app to be indexed; skipped when `sidx.allowIndexing=true`. */
    fun check(device: UiDevice) {
        if (required) waitIdle(device)
    }

    /** Waits until `sidx_indexing_banner` has been absent for 3 s (max 10 min), else fails. */
    fun waitIdle(device: UiDevice, maxWaitMs: Long = MAX_WAIT_MS) {
        val start = SystemClock.elapsedRealtime()
        var absentSince = -1L
        while (SystemClock.elapsedRealtime() - start < maxWaitMs) {
            val now = SystemClock.elapsedRealtime()
            if (device.hasObject(By.res(Tags.INDEXING_BANNER))) {
                absentSince = -1L
            } else {
                if (absentSince < 0) absentSince = now
                if (now - absentSince >= ABSENT_FOR_MS) return
            }
            SystemClock.sleep(POLL_MS)
        }
        throw AssertionError(
            "Indexing still running after ${maxWaitMs / 1000} s: seed and index first (perf-procedures §6.6)",
        )
    }
}
