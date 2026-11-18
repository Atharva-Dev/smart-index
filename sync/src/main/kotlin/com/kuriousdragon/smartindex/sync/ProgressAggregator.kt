package com.kuriousdragon.smartindex.sync

import com.kuriousdragon.smartindex.core.model.ProgressSink
import kotlin.math.max

/**
 * Hands out one thread-safe [ProgressSink] per planned indexer (slot = plan index) and reports the summed
 * progress through [onChange]. [onChange] is called outside the internal lock.
 */
class ProgressAggregator(private val onChange: (done: Int, total: Int) -> Unit) {
    private val lock = Any()
    private val done = HashMap<Int, Int>()
    private val total = HashMap<Int, Int>()

    /** Clears all slots. */
    fun reset() {
        synchronized(lock) {
            done.clear()
            total.clear()
        }
    }

    /** The sink for [slot]; each report replaces that slot's previous values. */
    fun sinkFor(slot: Int): ProgressSink = ProgressSink { d, t -> report(slot, d, t) }

    private fun report(slot: Int, d: Int, t: Int) {
        val sumDone: Long
        val sumTotal: Long
        synchronized(lock) {
            done[slot] = max(d, 0)
            total[slot] = max(t, 0)
            sumDone = done.values.sumOf { it.toLong() }
            sumTotal = total.values.sumOf { it.toLong() }
        }
        onChange(sumDone.toClamped(), max(sumTotal, sumDone).toClamped())
    }

    private fun Long.toClamped(): Int = if (this > Int.MAX_VALUE) Int.MAX_VALUE else toInt()
}
