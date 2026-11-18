package com.kuriousdragon.smartindex.sync

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import org.junit.Test

class ProgressAggregatorTest {
    private val seen = CopyOnWriteArrayList<Pair<Int, Int>>()
    private val aggregator = ProgressAggregator { d, t -> seen += d to t }

    @Test
    fun `sums the reports of two slots`() {
        aggregator.sinkFor(0).report(10, 100)
        aggregator.sinkFor(1).report(5, 50)
        assertThat(seen.last()).isEqualTo(15 to 150)
    }

    @Test
    fun `a later report replaces the earlier one for the same slot`() {
        val sink = aggregator.sinkFor(0)
        sink.report(10, 100)
        sink.report(20, 100)
        assertThat(seen.last()).isEqualTo(20 to 100)
    }

    @Test
    fun `unknown total makes the total equal to done`() {
        aggregator.sinkFor(0).report(7, 0)
        assertThat(seen.last()).isEqualTo(7 to 7)
    }

    @Test
    fun `negative values clamp to zero`() {
        aggregator.sinkFor(0).report(-3, -9)
        assertThat(seen.last()).isEqualTo(0 to 0)
    }

    @Test
    fun `reset clears all slots`() {
        aggregator.sinkFor(0).report(10, 100)
        aggregator.reset()
        aggregator.sinkFor(1).report(1, 2)
        assertThat(seen.last()).isEqualTo(1 to 2)
    }

    @Test
    fun `sums clamp to Int MAX_VALUE`() {
        aggregator.sinkFor(0).report(Int.MAX_VALUE, Int.MAX_VALUE)
        aggregator.sinkFor(1).report(Int.MAX_VALUE, Int.MAX_VALUE)
        assertThat(seen.last()).isEqualTo(Int.MAX_VALUE to Int.MAX_VALUE)
    }

    @Test
    fun `concurrent reports from 8 threads on 8 sinks produce the exact final sums`() {
        val start = CountDownLatch(1)
        val threads = (0 until 8).map { slot ->
            thread {
                val sink = aggregator.sinkFor(slot)
                start.await()
                for (i in 1..1000) sink.report(i, 1000)
            }
        }
        start.countDown()
        threads.forEach { it.join() }
        assertThat(seen.last()).isEqualTo(8 * 1000 to 8 * 1000)
    }
}
