package com.kuriousdragon.smartindex.feature.home.state

import androidx.paging.LoadState
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.SearchQuery
import com.kuriousdragon.smartindex.feature.home.model.DateFilter
import com.kuriousdragon.smartindex.feature.home.model.ListRequest
import com.kuriousdragon.smartindex.feature.home.model.Tab
import com.kuriousdragon.smartindex.feature.home.testing.TestEnv
import org.junit.Test

class ListStatusTest {
    private val notLoading = LoadState.NotLoading(endOfPaginationReached = false)
    private val timeline = ListRequest.Timeline(TestEnv.today, null)
    private val search = ListRequest.Search(SearchQuery("ra", null, null))

    @Suppress("LongParameterList")
    private fun resolve(
        request: ListRequest = timeline,
        refresh: LoadState = notLoading,
        itemCount: Int = 0,
        skeletonDue: Boolean = false,
        counts: Map<ItemType, Int>? = null,
        mode: HomeMode = HomeMode.HOME,
        text: String = "",
        filter: DateFilter = DateFilter.AnyDate,
        tab: Tab = Tab.ALL,
        groups: Map<Group, GroupState> = emptyMap(),
    ) = resolveListStatus(
        ListStatusInput(request, refresh, itemCount, skeletonDue, counts, mode, text, filter, tab, groups),
    )

    @Test
    fun `row 1 hint wins over everything`() {
        val status = resolve(
            request = ListRequest.Hint,
            refresh = LoadState.Error(RuntimeException()),
            itemCount = 3,
            mode = HomeMode.SEARCH,
        )
        assertThat(status).isEqualTo(ListStatus.Hint)
    }

    @Test
    fun `row 2 locked tab carries the group state`() {
        val request = ListRequest.Locked(Tab.CALLS, Group.CALL_LOG)
        val denied = resolve(request, groups = mapOf(Group.CALL_LOG to GroupState.DENIED_PERMANENTLY), tab = Tab.CALLS)
        assertThat(denied).isEqualTo(ListStatus.TabLocked(Tab.CALLS, Group.CALL_LOG, GroupState.DENIED_PERMANENTLY))
        val unknown = resolve(request, refresh = LoadState.Error(RuntimeException()), tab = Tab.CALLS)
        assertThat(unknown).isEqualTo(ListStatus.TabLocked(Tab.CALLS, Group.CALL_LOG, GroupState.DENIED))
    }

    @Test
    fun `row 3 error depends on the mode and beats loading and rows`() {
        val error = LoadState.Error(RuntimeException())
        assertThat(resolve(refresh = error, itemCount = 5)).isEqualTo(ListStatus.Error(ErrorKind.DAY))
        assertThat(resolve(search, error, mode = HomeMode.SEARCH, text = "ra"))
            .isEqualTo(ListStatus.Error(ErrorKind.SEARCH))
    }

    @Test
    fun `row 4 skeleton only once due, 6 rows in Home and 5 in Search`() {
        assertThat(resolve(refresh = LoadState.Loading, skeletonDue = true, itemCount = 4))
            .isEqualTo(ListStatus.Skeleton(6))
        assertThat(resolve(search, LoadState.Loading, skeletonDue = true, mode = HomeMode.SEARCH, text = "ra"))
            .isEqualTo(ListStatus.Skeleton(5))
    }

    @Test
    fun `row 5 rows are content, also while a new refresh is loading but not due`() {
        assertThat(resolve(itemCount = 3)).isEqualTo(ListStatus.Content)
        assertThat(resolve(refresh = LoadState.Loading, itemCount = 3, skeletonDue = false))
            .isEqualTo(ListStatus.Content)
    }

    @Test
    fun `row 6 loading with no rows and no skeleton yet is empty content`() {
        assertThat(resolve(refresh = LoadState.Loading, itemCount = 0, skeletonDue = false))
            .isEqualTo(ListStatus.Content)
    }

    @Test
    fun `row 7 empty day on ALL or when the counts say the day is empty`() {
        assertThat(resolve(tab = Tab.ALL)).isEqualTo(ListStatus.Empty(EmptyKind.EMPTY_DAY))
        val zero = ItemType.entries.associateWith { 0 }
        assertThat(resolve(tab = Tab.PHOTOS, counts = zero)).isEqualTo(ListStatus.Empty(EmptyKind.EMPTY_DAY))
    }

    @Test
    fun `row 8 empty tab when the day has other items or the counts are unknown`() {
        val some = ItemType.entries.associateWith { if (it == ItemType.CALL) 2 else 0 }
        assertThat(resolve(tab = Tab.PHOTOS, counts = some)).isEqualTo(ListStatus.Empty(EmptyKind.EMPTY_TAB))
        assertThat(resolve(tab = Tab.PHOTOS, counts = null)).isEqualTo(ListStatus.Empty(EmptyKind.EMPTY_TAB))
    }

    @Test
    fun `row 9 date only search is an empty range`() {
        assertThat(resolve(search, mode = HomeMode.SEARCH, text = "  ", filter = DateFilter.Today))
            .isEqualTo(ListStatus.Empty(EmptyKind.EMPTY_RANGE))
    }

    @Test
    fun `row 10 search with text and no rows is no results`() {
        assertThat(resolve(search, mode = HomeMode.SEARCH, text = "ra"))
            .isEqualTo(ListStatus.Empty(EmptyKind.NO_RESULTS))
        assertThat(resolve(search, mode = HomeMode.SEARCH, text = "ra", filter = DateFilter.Today, tab = Tab.CALLS))
            .isEqualTo(ListStatus.Empty(EmptyKind.NO_RESULTS))
    }
}
