package com.kuriousdragon.smartindex.feature.home.pipeline

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.IndexStats
import com.kuriousdragon.smartindex.core.model.IndexStatus
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.SearchQuery
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.feature.home.model.Commit
import com.kuriousdragon.smartindex.feature.home.model.CountsKey
import com.kuriousdragon.smartindex.feature.home.model.Grouping
import com.kuriousdragon.smartindex.feature.home.model.ListRequest
import com.kuriousdragon.smartindex.feature.home.testing.FakeIndexQuery
import com.kuriousdragon.smartindex.feature.home.testing.TestEnv
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CountsLoaderTest {
    private val index = FakeIndexQuery()
    private val loader = CountsLoader(index)
    private val commits = MutableSharedFlow<Pair<Commit, CountsKey?>>()
    private val settled = MutableSharedFlow<Int>()
    private val ticks = MutableSharedFlow<Unit>()
    private val results = mutableListOf<CountsResult>()
    private val logged = mutableListOf<String>()
    private val oldSink = SiLog.sink

    private val day1 = LocalDate.of(2026, 11, 1)
    private val day2 = LocalDate.of(2026, 11, 2)

    @Before
    fun setUp() {
        SiLog.sink = SiLog.Sink { _, _, message -> logged += message }
    }

    @After
    fun tearDown() {
        SiLog.sink = oldSink
    }

    private fun commit(id: Int) = Commit(id, ListRequest.Hint, Grouping.DATE, TestEnv.env, null)

    private fun TestScope.start() {
        backgroundScope.launch { loader.counts(commits, settled, ticks).toList(results) }
        runCurrent()
    }

    private suspend fun send(id: Int, key: CountsKey?) = commits.emit(commit(id) to key)

    private fun counts(n: Int) = ItemType.entries.associateWith { n }

    @Test
    fun `first key emits null then the counts`() = runTest {
        index.dayCounts = { counts(2) }
        start()
        send(1, CountsKey.Day(day1))
        runCurrent()
        assertThat(results).containsExactly(CountsResult(1, null), CountsResult(1, counts(2))).inOrder()
        assertThat(index.countsCalls).containsExactly(CountsKey.Day(day1))
    }

    @Test
    fun `a different key emits null first`() = runTest {
        start()
        send(1, CountsKey.Day(day1))
        runCurrent()
        send(2, CountsKey.Day(day2))
        runCurrent()
        assertThat(results.map { it.commitId to (it.counts == null) })
            .containsExactly(1 to true, 1 to false, 2 to true, 2 to false).inOrder()
    }

    @Test
    fun `a tick for the same key recomputes without a null`() = runTest {
        var n = 1
        index.dayCounts = { counts(n) }
        start()
        send(1, CountsKey.Day(day1))
        runCurrent()
        n = 5
        ticks.emit(Unit)
        runCurrent()
        assertThat(results).containsExactly(
            CountsResult(1, null),
            CountsResult(1, counts(1)),
            CountsResult(1, counts(5)),
        ).inOrder()
        assertThat(index.countsCalls).hasSize(2)
    }

    @Test
    fun `the same key on a new commit keeps the badges`() = runTest {
        start()
        send(1, CountsKey.Day(day1))
        runCurrent()
        send(2, CountsKey.Day(day1))
        runCurrent()
        assertThat(results.count { it.counts == null }).isEqualTo(1)
        assertThat(results.last().commitId).isEqualTo(2)
    }

    @Test
    fun `a null key hides the badges and queries nothing`() = runTest {
        start()
        send(1, null)
        runCurrent()
        assertThat(results).containsExactly(CountsResult(1, null))
        assertThat(index.countsCalls).isEmpty()
    }

    @Test
    fun `a search key waits for the first page to settle`() = runTest {
        start()
        send(3, CountsKey.Query(SearchQuery("ra", null, null)))
        runCurrent()
        assertThat(index.countsCalls).isEmpty()
        settled.emit(2)
        runCurrent()
        assertThat(index.countsCalls).isEmpty()
        settled.emit(3)
        runCurrent()
        assertThat(index.countsCalls).hasSize(1)
        assertThat(results.last().counts).isNotNull()
    }

    @Test
    fun `a search key stops waiting after 1_5 seconds`() = runTest {
        start()
        send(3, CountsKey.Query(SearchQuery("rah", null, null)))
        runCurrent()
        advanceTimeBy(1_499)
        assertThat(index.countsCalls).isEmpty()
        advanceTimeBy(2)
        runCurrent()
        assertThat(index.countsCalls).hasSize(1)
    }

    @Test
    fun `a short search key does not start counts before the first page, however long it takes`() = runTest {
        start()
        send(3, CountsKey.Query(SearchQuery("q", null, null)))
        runCurrent()
        advanceTimeBy(1_501)
        runCurrent()
        assertThat(index.countsCalls).isEmpty()
        advanceTimeBy(29_999 - 1_501)
        runCurrent()
        assertThat(currentTime).isEqualTo(29_999)
        assertThat(index.countsCalls).isEmpty()
        settled.emit(3)
        runCurrent()
        assertThat(index.countsCalls).hasSize(1)
        assertThat(results.last().counts).isNotNull()
    }

    @Test
    fun `a short search key starts counts at 30 seconds when the page never settles`() = runTest {
        start()
        send(3, CountsKey.Query(SearchQuery("qz", null, null)))
        runCurrent()
        advanceTimeBy(29_999)
        runCurrent()
        assertThat(index.countsCalls).isEmpty()
        advanceTimeBy(1)
        runCurrent()
        assertThat(index.countsCalls).hasSize(1)
    }

    @Test
    fun `a new commit while a short key waits cancels the wait without counting the old key`() = runTest {
        start()
        val old = CountsKey.Query(SearchQuery("qz", null, null))
        send(3, old)
        runCurrent()
        advanceTimeBy(10_000)
        send(4, CountsKey.Day(day1))
        runCurrent()
        advanceTimeBy(40_000)
        runCurrent()
        assertThat(index.countsCalls).containsExactly(CountsKey.Day(day1))
    }

    @Test
    fun `a home key does not wait for the page`() = runTest {
        start()
        send(1, CountsKey.Day(day1))
        runCurrent()
        assertThat(currentTime).isEqualTo(0)
        assertThat(index.countsCalls).hasSize(1)
    }

    @Test
    fun `a new commit cancels the old load and drops its result`() = runTest {
        val gate = CompletableDeferred<Unit>()
        index.countsGate = { gate.await() }
        index.dayCounts = { day -> counts(if (day == day1) 1 else 2) }
        start()
        send(1, CountsKey.Day(day1))
        runCurrent()
        send(2, CountsKey.Day(day2))
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertThat(results.filter { it.counts != null }).containsExactly(CountsResult(2, counts(2)))
    }

    @Test
    fun `a failure emits null and logs only the class name`() = runTest {
        index.failCountsNext = IOException("secret path /sdcard/x")
        start()
        send(1, CountsKey.Day(day1))
        runCurrent()
        assertThat(results.last()).isEqualTo(CountsResult(1, null))
        assertThat(logged).containsExactly("counts failed class=IOException")
    }

    @Test
    fun `the loader keeps working after a failure`() = runTest {
        index.failCountsNext = IOException()
        start()
        send(1, CountsKey.Day(day1))
        runCurrent()
        ticks.emit(Unit)
        runCurrent()
        assertThat(results.last().counts).isNotNull()
    }

    @Test
    fun `index ticks fire on the leading edge and at most once a second`() = runTest {
        val status = MutableStateFlow(IndexStatus(stats = stats(1)))
        val fired = mutableListOf<Long>()
        backgroundScope.launch { indexTicks(status).collect { fired += currentTime } }
        runCurrent()
        assertThat(fired).containsExactly(0L)
        advanceTimeBy(100)
        status.value = IndexStatus(stats = stats(2))
        advanceTimeBy(100)
        status.value = IndexStatus(stats = stats(3))
        advanceTimeBy(500)
        assertThat(fired).containsExactly(0L)
        advanceTimeBy(300)
        runCurrent()
        assertThat(fired).containsExactly(0L, 1_000L).inOrder()
    }

    @Test
    fun `index ticks ignore status changes that keep the same total`() = runTest {
        val status = MutableStateFlow(IndexStatus(stats = stats(1)))
        val fired = mutableListOf<Long>()
        backgroundScope.launch { indexTicks(status).collect { fired += currentTime } }
        runCurrent()
        advanceTimeBy(1_500)
        status.value = IndexStatus(done = 5, stats = stats(1))
        advanceTimeBy(2_000)
        assertThat(fired).containsExactly(0L)
    }

    private fun stats(total: Int) = IndexStats(ItemType.entries.associateWith { 0 }, 0, total)
}
