package com.kuriousdragon.smartindex.source.recordings.match

import com.kuriousdragon.smartindex.core.model.RecordingInputs

/** A call with its identity data computed once (LLD §3.5). */
internal data class PreparedCall(val call: RecordingInputs.Call, val identity: CallIdentity)

/** Calls sorted by `(primaryTs, id)` with a parallel timestamp array for binary search (LLD §4.6). */
internal class CallTimeline(calls: List<PreparedCall>) {
    @PublishedApi internal val items: List<PreparedCall> = calls

    @PublishedApi internal val timestamps: LongArray = LongArray(calls.size) { calls[it].call.primaryTs }

    val size: Int get() = items.size

    /** First index whose timestamp is >= [t]; [size] when there is none. */
    fun lowerBound(t: Long): Int {
        var lo = 0
        var hi = timestamps.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (timestamps[mid] < t) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** Calls with `fromMs <= primaryTs <= toMsInclusive`, in order. */
    inline fun forEachInRange(fromMs: Long, toMsInclusive: Long, block: (PreparedCall) -> Unit) {
        var i = lowerBound(fromMs)
        while (i < timestamps.size && timestamps[i] <= toMsInclusive) {
            block(items[i])
            i++
        }
    }
}
