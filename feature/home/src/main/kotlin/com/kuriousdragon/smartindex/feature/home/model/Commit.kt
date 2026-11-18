package com.kuriousdragon.smartindex.feature.home.model

import androidx.compose.runtime.Immutable
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.IndexConfig
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.SearchQuery
import com.kuriousdragon.smartindex.core.model.TypeGating
import com.kuriousdragon.smartindex.feature.home.state.HomeMode
import java.time.LocalDate

/** What the list area queries for (LLD — Home & Search UI §3.1). */
sealed interface ListRequest {
    data class Timeline(val day: LocalDate, val type: ItemType?) : ListRequest

    /** Text non-blank OR range != null. */
    data class Search(val query: SearchQuery) : ListRequest

    /** Search state, blank text, any date: show the hint, no query. */
    data object Hint : ListRequest

    /** The tab's group is not usable: show the permission state, no query. */
    data class Locked(val tab: Tab, val group: Group) : ListRequest
}

/** One committed list request. */
@Immutable
data class Commit(
    /** Equals the trace cookie; strictly increasing per ViewModel. */
    val id: Int,
    val request: ListRequest,
    /** Timeline: PART_OF_DAY; everything else: DATE. */
    val grouping: Grouping,
    val env: DeviceEnv,
    /** PerfTrace.SEARCH_FIRST_PAGE, TIMELINE_FIRST_PAGE or null (Hint, Locked, tab-only change in Home). */
    val traceSection: String?,
)

/** Everything [deriveRequest] and [countsKey] depend on. [text] is trimmed. */
data class CommitInputs(
    val mode: HomeMode,
    val day: LocalDate,
    val text: String,
    val filter: DateFilter,
    val tab: Tab,
    val groups: Map<Group, GroupState>,
    val env: DeviceEnv,
)

/** What the per-tab counts are for. */
sealed interface CountsKey {
    data class Day(val day: LocalDate) : CountsKey

    /** A search query with [SearchQuery.type] = null. */
    data class Query(val query: SearchQuery) : CountsKey {
        /** True for a 1-2 code point query (substring scan): its page and counts scans must not overlap. */
        val isShort: Boolean
            get() = query.text.trim().let { it.codePointCount(0, it.length) } in
                1..IndexConfig.SHORT_QUERY_MAX_CODE_POINTS
    }
}

/** Pure. Rules in LLD §4.2, in this order. */
internal fun deriveRequest(i: CommitInputs): ListRequest {
    val group = i.tab.group
    if (group != null && !TypeGating.isUsable(i.groups[group])) return ListRequest.Locked(i.tab, group)
    if (i.mode == HomeMode.HOME) return ListRequest.Timeline(i.day, i.tab.type)
    val range = i.filter.resolve(i.env.today)?.toDateRange(i.env.zone)
    if (i.text.isBlank() && range == null) return ListRequest.Hint
    return ListRequest.Search(SearchQuery(i.text, range, i.tab.type))
}

/** Day(day) in Home, Query(type = null) in Search, null for Hint. A locked tab still has a key. */
internal fun countsKey(i: CommitInputs): CountsKey? {
    if (i.mode == HomeMode.HOME) return CountsKey.Day(i.day)
    val range = i.filter.resolve(i.env.today)?.toDateRange(i.env.zone)
    if (i.text.isBlank() && range == null) return null
    return CountsKey.Query(SearchQuery(i.text, range, null))
}
