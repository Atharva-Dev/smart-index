package com.kuriousdragon.smartindex.core.index.query

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.testing.TestPager
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.index.IndexDbTest
import com.kuriousdragon.smartindex.core.index.db.IndexDatabase
import com.kuriousdragon.smartindex.core.model.ResultItem
import com.kuriousdragon.smartindex.core.model.SearchQuery
import com.kuriousdragon.smartindex.core.model.Source
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Offset paging, ordering, invalidation and failure of [IndexPagingSource] (LLD §5.7, §7.3). */
class PagingTest : IndexDbTest() {
    private val config =
        PagingConfig(pageSize = 50, initialLoadSize = 50, prefetchDistance = 15, enablePlaceholders = false)

    private val signal by lazy { IndexChangeSignal(db) }

    private fun source(q: SearchQuery, database: IndexDatabase = db) =
        IndexPagingSource(SearchPlan.of(q), database, signal)

    /**
     * Writes [n] messages, then lets the change signal go quiet. The signal only sees writes made after its
     * tracker subscription is live, so harmless re-writes of message 1 are made until it reports one; the extra
     * wait lets any change still queued in the throttle window be delivered before a source is created.
     */
    private fun seed(n: Int) {
        val seen = java.util.concurrent.CountDownLatch(1)
        val unregister = signal.register { seen.countDown() }
        runBlocking { writer.upsert((1L..n).map { smsAt(it, 1_000_000 + it) }) }
        var version = 1L
        val deadline = System.currentTimeMillis() + 10_000
        while (!seen.await(250, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            assertThat(System.currentTimeMillis()).isLessThan(deadline)
            runBlocking { writer.upsert(listOf(smsAt(1, 1_000_001).copy(sourceVersion = ++version))) }
        }
        Thread.sleep(IndexChangeSignal.THROTTLE_MS + 500)
        unregister()
    }

    @Test
    fun `120 matches load as 50 50 20`() {
        seed(120)
        val pager = TestPager(config, source(SearchQuery("hotel", null, null)))
        runBlocking {
            val first = pager.refresh() as PagingSource.LoadResult.Page
            val second = pager.append() as PagingSource.LoadResult.Page
            val third = pager.append() as PagingSource.LoadResult.Page
            assertThat(listOf(first.data.size, second.data.size, third.data.size)).containsExactly(50, 50, 20).inOrder()
            assertThat(first.prevKey).isNull()
            assertThat(first.nextKey).isEqualTo(50)
            assertThat(third.nextKey).isNull()
            assertThat(pager.getPages().flatMap { it.data }.map { it.id }.toSet()).hasSize(120)
        }
    }

    @Test
    fun `search results are ordered by time then id, newest first, including ties`() {
        runBlocking {
            writer.upsert(listOf(smsAt(1, 5_000), smsAt(2, 5_000), smsAt(3, 9_000), smsAt(4, 1_000), smsAt(5, 5_000)))
        }
        val items = query.search(SearchQuery("hotel", null, null)).all()
        val id = { n: Long -> idOf(Source.SMS, n) }
        assertThat(items.map { it.id }).containsExactly(id(3), id(5), id(2), id(1), id(4)).inOrder()
    }

    @Test
    fun `the timeline pages in order`() {
        val day = LocalDate.of(2026, 3, 10)
        runBlocking { writer.upsert((1L..75L).map { smsAt(it, at(day, 0) + it * 1_000) }) }
        val items = query.timeline(day, null).all()
        assertThat(items).hasSize(75)
        assertThat(items.map { it.time }).isInOrder()
    }

    @Test
    fun `getRefreshKey anchors 25 rows before the anchor position`() {
        seed(120)
        val src = source(SearchQuery("hotel", null, null))
        val page =
            runBlocking { src.load(PagingSource.LoadParams.Refresh(0, 50, false)) as PagingSource.LoadResult.Page }
        fun state(anchor: Int?) = PagingState(listOf(page), anchor, config, 0)
        assertThat(src.getRefreshKey(state(60))).isEqualTo(35)
        assertThat(src.getRefreshKey(state(10))).isEqualTo(0)
        assertThat(src.getRefreshKey(state(null))).isNull()
    }

    @Test
    fun `an upsert invalidates the source and a refreshed snapshot has the new item`() {
        seed(3)
        val src = source(SearchQuery("hotel", null, null))
        Thread.sleep(IndexChangeSignal.THROTTLE_MS + 200)
        var invalidated = false
        src.registerInvalidatedCallback { invalidated = true }
        runBlocking { writer.upsert(listOf(smsAt(99, 9_999_999, body = "Hotel again"))) }
        val deadline = System.currentTimeMillis() + 10_000
        while (!invalidated && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertThat(invalidated).isTrue()
        assertThat(src.invalid).isTrue()
        val fresh = query.search(SearchQuery("hotel", null, null)).all()
        assertThat(fresh.first().id).isEqualTo(idOf(Source.SMS, 99))
        assertThat(fresh).hasSize(4)
    }

    @Test
    fun `a failing query returns an Error`() {
        seed(3)
        val src = source(SearchQuery("ho", null, null))
        read { c -> c.usePrepared("DROP TABLE short_text") { it.step() } }
        val result = runBlocking { src.load(PagingSource.LoadParams.Refresh(null, 50, false)) }
        assertThat(result).isInstanceOf(PagingSource.LoadResult.Error::class.java)
    }

    @Test
    fun `a load on a closed database is cancelled, never swallowed as data`() {
        seed(3)
        val src = source(SearchQuery("hotel", null, null))
        db.close()
        val outcome = runCatching { runBlocking { src.load(PagingSource.LoadParams.Refresh(null, 50, false)) } }
        val result = outcome.getOrNull()
        assertThat(result is PagingSource.LoadResult.Page<*, *>).isFalse()
    }

    @Test
    fun `prepend loads the rows before the key`() {
        seed(120)
        val src = source(SearchQuery("hotel", null, null))
        runBlocking {
            val page = src.load(
                PagingSource.LoadParams.Prepend(60, 50, false),
            ) as PagingSource.LoadResult.Page<Int, ResultItem>
            assertThat(page.data).hasSize(50)
            assertThat(page.prevKey).isEqualTo(10)
            assertThat(page.nextKey).isEqualTo(60)
        }
    }
}
