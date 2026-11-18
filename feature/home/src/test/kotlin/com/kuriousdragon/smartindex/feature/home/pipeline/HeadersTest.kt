package com.kuriousdragon.smartindex.feature.home.pipeline

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.testing.TestPager
import androidx.paging.testing.asSnapshot
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.ResultItem
import com.kuriousdragon.smartindex.feature.home.model.Grouping
import com.kuriousdragon.smartindex.feature.home.model.ListEntry
import com.kuriousdragon.smartindex.feature.home.model.ListGroup
import com.kuriousdragon.smartindex.feature.home.model.PartOfDay
import com.kuriousdragon.smartindex.feature.home.testing.TestEnv
import com.kuriousdragon.smartindex.feature.home.testing.londonTime
import com.kuriousdragon.smartindex.feature.home.testing.photo
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HeadersTest {
    private val zone = TestEnv.zone

    private fun snapshot(items: List<ResultItem>, grouping: Grouping) = flowOf(PagingData.from(items))
        .map { it.withHeaders(grouping, zone) }

    private fun part(p: PartOfDay) = ListEntry.Header(ListGroup.PartOfDayGroup(p))

    private fun date(d: String) = ListEntry.Header(ListGroup.DateGroup(LocalDate.parse(d)))

    @Test
    fun `header comes first and between groups but never at the end`() = runTest {
        val morning = photo(1, londonTime("2026-11-03", "09:00"))
        val morning2 = photo(2, londonTime("2026-11-03", "10:00"))
        val evening = photo(3, londonTime("2026-11-03", "18:00"))
        val result = snapshot(listOf(morning, morning2, evening), Grouping.PART_OF_DAY).asSnapshot()
        assertThat(result).containsExactly(
            part(PartOfDay.MORNING),
            ListEntry.Row(morning),
            ListEntry.Row(morning2),
            part(PartOfDay.EVENING),
            ListEntry.Row(evening),
        ).inOrder()
    }

    @Test
    fun `an empty list has no header`() = runTest {
        assertThat(snapshot(emptyList(), Grouping.PART_OF_DAY).asSnapshot()).isEmpty()
    }

    @Test
    fun `date grouping uses one header per local date`() = runTest {
        val a = photo(1, londonTime("2026-11-02", "23:30"))
        val b = photo(2, londonTime("2026-11-03", "00:10"))
        val c = photo(3, londonTime("2026-11-03", "23:59"))
        val result = snapshot(listOf(a, b, c), Grouping.DATE).asSnapshot()
        assertThat(result).containsExactly(
            date("2026-11-02"),
            ListEntry.Row(a),
            date("2026-11-03"),
            ListEntry.Row(b),
            ListEntry.Row(c),
        ).inOrder()
    }

    @Test
    fun `part of day grouping uses the boundaries`() = runTest {
        val a = photo(1, londonTime("2026-11-03", "04:59"))
        val b = photo(2, londonTime("2026-11-03", "05:00"))
        val result = snapshot(listOf(a, b), Grouping.PART_OF_DAY).asSnapshot()
        assertThat(result.filterIsInstance<ListEntry.Header>())
            .containsExactly(part(PartOfDay.EARLY_HOURS), part(PartOfDay.MORNING)).inOrder()
    }

    private class ItemsSource(private val items: List<ResultItem>) : PagingSource<Int, ResultItem>() {
        override fun getRefreshKey(state: PagingState<Int, ResultItem>): Int? = null

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ResultItem> {
            val start = params.key ?: 0
            val end = minOf(start + params.loadSize, items.size)
            return LoadResult.Page(
                data = items.subList(start, end),
                prevKey = null,
                nextKey = if (end >= items.size) null else end,
            )
        }
    }

    private val pageConfig = PagingConfig(pageSize = 50, initialLoadSize = 50, enablePlaceholders = false)

    @Test
    fun `one header spans a 50 item page boundary`() = runTest {
        val items = (1L..100L).map { photo(it, londonTime("2026-11-03", "09:00")) }
        val result = Pager(pageConfig) { ItemsSource(items) }.flow
            .map { it.withHeaders(Grouping.PART_OF_DAY, zone) }
            .asSnapshot { scrollTo(index = 60) }
        assertThat(result.filterIsInstance<ListEntry.Header>()).containsExactly(part(PartOfDay.MORNING))
        assertThat(result.filterIsInstance<ListEntry.Row>().size).isGreaterThan(50)
        assertThat(result.first()).isEqualTo(part(PartOfDay.MORNING))
    }

    @Test
    fun `a new group starting on the second page gets its header`() = runTest {
        val items = (1L..50L).map { photo(it, londonTime("2026-11-03", "09:00")) } +
            (51L..100L).map { photo(it, londonTime("2026-11-03", "18:00")) }
        val result = Pager(pageConfig) { ItemsSource(items) }.flow
            .map { it.withHeaders(Grouping.PART_OF_DAY, zone) }
            .asSnapshot { scrollTo(index = 60) }
        assertThat(result.filterIsInstance<ListEntry.Header>())
            .containsExactly(part(PartOfDay.MORNING), part(PartOfDay.EVENING)).inOrder()
        val eveningAt = result.indexOf(part(PartOfDay.EVENING))
        assertThat(result[eveningAt - 1]).isEqualTo(ListEntry.Row(items[49]))
        assertThat(result[eveningAt + 1]).isEqualTo(ListEntry.Row(items[50]))
    }

    @Test
    fun `the test pager serves the source pages in order`() = runTest {
        val items = (1L..100L).map { photo(it) }
        val pager = TestPager(pageConfig, ItemsSource(items))
        val first = pager.refresh() as PagingSource.LoadResult.Page
        val second = pager.append() as PagingSource.LoadResult.Page
        assertThat(first.data).hasSize(50)
        assertThat(second.data.first()).isEqualTo(items[50])
    }
}
