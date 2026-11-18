package com.kuriousdragon.smartindex.feature.home.model

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.DateRange
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.SearchQuery
import com.kuriousdragon.smartindex.feature.home.state.HomeMode
import com.kuriousdragon.smartindex.feature.home.testing.TestEnv
import org.junit.Test

class CommitTest {
    private val granted = Group.entries.associateWith { GroupState.GRANTED }
    private val todayRange = DateRange(
        startMs = TestEnv.today.atStartOfDay(TestEnv.zone).toInstant().toEpochMilli(),
        endMsInclusive = TestEnv.today.plusDays(1).atStartOfDay(TestEnv.zone).toInstant().toEpochMilli() - 1,
    )

    private fun inputs(
        mode: HomeMode = HomeMode.HOME,
        text: String = "",
        filter: DateFilter = DateFilter.AnyDate,
        tab: Tab = Tab.ALL,
        groups: Map<Group, GroupState> = granted,
    ) = CommitInputs(mode, TestEnv.today.minusDays(2), text, filter, tab, groups, TestEnv.env)

    private fun isShort(text: String) = CountsKey.Query(SearchQuery(text, null, null)).isShort

    @Test
    fun `a counts key is short for one or two code points after trimming`() {
        assertThat(isShort("q")).isTrue()
        assertThat(isShort("qz")).isTrue()
        assertThat(isShort("\uD83D\uDE00\uD83D\uDE00")).isTrue() // two emoji, four UTF-16 units
        assertThat(isShort("\u0939\u094B")).isTrue() // Devanagari
        assertThat(isShort(" q ")).isTrue()
        assertThat(isShort("qzx")).isFalse()
        assertThat(isShort("")).isFalse()
        assertThat(isShort("   ")).isFalse()
        assertThat(CountsKey.Query(SearchQuery("", todayRange, null)).isShort).isFalse()
    }

    @Test
    fun `home is a timeline of the day with the tab type`() {
        assertThat(deriveRequest(inputs())).isEqualTo(ListRequest.Timeline(TestEnv.today.minusDays(2), null))
        assertThat(deriveRequest(inputs(tab = Tab.CALLS)))
            .isEqualTo(ListRequest.Timeline(TestEnv.today.minusDays(2), ItemType.CALL))
    }

    @Test
    fun `a locked tab wins over everything in Home`() {
        val groups = granted + (Group.CALL_LOG to GroupState.DENIED)
        assertThat(deriveRequest(inputs(tab = Tab.CALLS, groups = groups)))
            .isEqualTo(ListRequest.Locked(Tab.CALLS, Group.CALL_LOG))
    }

    @Test
    fun `a locked tab wins over Search text and date`() {
        val groups = granted + (Group.MESSAGES to GroupState.DENIED_PERMANENTLY)
        val request = deriveRequest(
            inputs(HomeMode.SEARCH, text = "hotel", filter = DateFilter.Today, tab = Tab.MESSAGES, groups = groups),
        )
        assertThat(request).isEqualTo(ListRequest.Locked(Tab.MESSAGES, Group.MESSAGES))
    }

    @Test
    fun `a missing group state counts as locked and PARTIAL is usable`() {
        assertThat(deriveRequest(inputs(tab = Tab.PHOTOS, groups = emptyMap())))
            .isEqualTo(ListRequest.Locked(Tab.PHOTOS, Group.PHOTOS_VIDEOS))
        val partial = granted + (Group.PHOTOS_VIDEOS to GroupState.PARTIAL)
        assertThat(deriveRequest(inputs(tab = Tab.PHOTOS, groups = partial)))
            .isInstanceOf(ListRequest.Timeline::class.java)
    }

    @Test
    fun `the ALL tab is never locked`() {
        val none = Group.entries.associateWith { GroupState.DENIED }
        assertThat(deriveRequest(inputs(tab = Tab.ALL, groups = none)))
            .isInstanceOf(ListRequest.Timeline::class.java)
    }

    @Test
    fun `blank text with any date is the hint`() {
        assertThat(deriveRequest(inputs(HomeMode.SEARCH))).isEqualTo(ListRequest.Hint)
        assertThat(deriveRequest(inputs(HomeMode.SEARCH, text = "   "))).isEqualTo(ListRequest.Hint)
    }

    @Test
    fun `text with any date is a search without range and the tab type passes through`() {
        assertThat(deriveRequest(inputs(HomeMode.SEARCH, text = "ra")))
            .isEqualTo(ListRequest.Search(SearchQuery("ra", null, null)))
        assertThat(deriveRequest(inputs(HomeMode.SEARCH, text = "ra", tab = Tab.VIDEOS)))
            .isEqualTo(ListRequest.Search(SearchQuery("ra", null, ItemType.VIDEO)))
    }

    @Test
    fun `date only search has a range and blank text`() {
        assertThat(deriveRequest(inputs(HomeMode.SEARCH, filter = DateFilter.Today)))
            .isEqualTo(ListRequest.Search(SearchQuery("", todayRange, null)))
    }

    @Test
    fun `text and date search carries both`() {
        assertThat(deriveRequest(inputs(HomeMode.SEARCH, text = "hotel", filter = DateFilter.Day(TestEnv.today))))
            .isEqualTo(ListRequest.Search(SearchQuery("hotel", todayRange, null)))
    }

    @Test
    fun `counts key in Home is the day and ignores the tab`() {
        val day = TestEnv.today.minusDays(2)
        assertThat(countsKey(inputs())).isEqualTo(CountsKey.Day(day))
        assertThat(countsKey(inputs(tab = Tab.PHOTOS))).isEqualTo(CountsKey.Day(day))
    }

    @Test
    fun `counts key in Search has no type`() {
        assertThat(countsKey(inputs(HomeMode.SEARCH, text = "ra", tab = Tab.CALLS)))
            .isEqualTo(CountsKey.Query(SearchQuery("ra", null, null)))
        assertThat(countsKey(inputs(HomeMode.SEARCH, filter = DateFilter.Today, tab = Tab.CALLS)))
            .isEqualTo(CountsKey.Query(SearchQuery("", todayRange, null)))
    }

    @Test
    fun `counts key is null for the hint and still exists for a locked tab`() {
        assertThat(countsKey(inputs(HomeMode.SEARCH))).isNull()
        val groups = granted + (Group.CALL_LOG to GroupState.DENIED)
        assertThat(countsKey(inputs(HomeMode.SEARCH, text = "ra", tab = Tab.CALLS, groups = groups)))
            .isEqualTo(CountsKey.Query(SearchQuery("ra", null, null)))
    }
}
