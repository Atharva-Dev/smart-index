package com.kuriousdragon.smartindex.perf

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.IndexPhase
import com.kuriousdragon.smartindex.core.model.IndexStats
import com.kuriousdragon.smartindex.core.model.IndexStatus
import com.kuriousdragon.smartindex.core.model.IndexStatusSource
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.Mode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PerfMarkersTest {
    private class FakeSource(initial: IndexStatus = IndexStatus()) : IndexStatusSource {
        val flow = MutableStateFlow(initial)
        override val status: StateFlow<IndexStatus> = flow
    }

    private val stats = IndexStats(
        countsByType = mapOf(ItemType.PHOTO to 10, ItemType.CALL to 3),
        recordingsLinked = 2,
        totalItems = 13,
    )

    private fun TestScope.markers(source: FakeSource, log: MutableList<String>) =
        PerfMarkers.start(
            source,
            this,
            clock = { currentTime },
            wallClock = { 1_000_000L + currentTime },
            log = { log += it },
        )

    private val allMissing = "PHOTO=-1 VIDEO=-1 DOCUMENT=-1 OTHER_FILE=-1 CALL=-1 RECORDING=-1 MESSAGE=-1"

    @Test
    fun `FULL run logs start phases and end with stats`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource()
        val log = mutableListOf<String>()
        val job = markers(source, log)
        advanceUntilIdle()
        assertThat(log).isEmpty()

        advanceTimeBy(100)
        source.flow.value = IndexStatus(phase = IndexPhase.PREPARING)
        advanceUntilIdle()
        advanceTimeBy(50)
        source.flow.value = IndexStatus(phase = IndexPhase.INDEXING, mode = Mode.FULL, done = 5, total = 10)
        advanceUntilIdle()
        advanceTimeBy(200)
        source.flow.value = IndexStatus(phase = IndexPhase.INDEXING, mode = Mode.FULL, done = 9, total = 10)
        advanceUntilIdle()
        advanceTimeBy(100)
        source.flow.value = IndexStatus(phase = IndexPhase.LINKING, mode = Mode.FULL, done = 10, total = 10)
        advanceUntilIdle()
        advanceTimeBy(150)
        source.flow.value = IndexStatus(phase = IndexPhase.IDLE, stats = stats)
        advanceUntilIdle()

        assertThat(log).containsExactly(
            "run start atMs=1000100",
            "phase PREPARING mode=null sinceStartMs=0 done=0 total=0",
            "phase INDEXING mode=FULL sinceStartMs=50 done=5 total=10",
            "phase LINKING mode=FULL sinceStartMs=350 done=10 total=10",
            "run end mode=FULL durationMs=500 total=13 PHOTO=10 VIDEO=-1 DOCUMENT=-1 OTHER_FILE=-1 " +
                "CALL=3 RECORDING=-1 MESSAGE=-1 linked=2 error=none",
        ).inOrder()
        job.cancel()
    }

    @Test
    fun `INCREMENTAL run logs its mode`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource()
        val log = mutableListOf<String>()
        val job = markers(source, log)
        advanceUntilIdle()
        source.flow.value = IndexStatus(phase = IndexPhase.INDEXING, mode = Mode.INCREMENTAL, done = 1, total = 2)
        advanceUntilIdle()
        advanceTimeBy(30)
        source.flow.value = IndexStatus(phase = IndexPhase.IDLE, stats = stats)
        advanceUntilIdle()

        assertThat(log).hasSize(3)
        assertThat(log[1]).isEqualTo("phase INDEXING mode=INCREMENTAL sinceStartMs=0 done=1 total=2")
        assertThat(log[2]).startsWith("run end mode=INCREMENTAL durationMs=30 total=13")
        job.cancel()
    }

    @Test
    fun `stats arriving one second after IDLE are used`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource(IndexStatus(stats = null))
        val log = mutableListOf<String>()
        val job = markers(source, log)
        advanceUntilIdle()
        source.flow.value = IndexStatus(phase = IndexPhase.INDEXING, mode = Mode.FULL, total = 4)
        advanceUntilIdle()
        advanceTimeBy(10)
        source.flow.value = IndexStatus(phase = IndexPhase.IDLE)
        runCurrent() // not advanceUntilIdle: that would skip past the 5 s stats wait
        assertThat(log.none { it.startsWith("run end") }).isTrue()

        advanceTimeBy(1_000)
        source.flow.value = IndexStatus(phase = IndexPhase.IDLE, stats = stats)
        advanceUntilIdle()

        assertThat(log.last()).startsWith("run end mode=FULL durationMs=10 total=13 PHOTO=10")
        job.cancel()
    }

    @Test
    fun `missing stats print minus one after the wait`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource()
        val log = mutableListOf<String>()
        val job = markers(source, log)
        advanceUntilIdle()
        source.flow.value = IndexStatus(phase = IndexPhase.INDEXING, mode = Mode.FULL)
        advanceUntilIdle()
        source.flow.value = IndexStatus(phase = IndexPhase.IDLE)
        advanceUntilIdle()

        assertThat(log.last()).isEqualTo(
            "run end mode=FULL durationMs=0 total=-1 $allMissing linked=-1 error=none",
        )
        job.cancel()
    }
}
