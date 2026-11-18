package com.kuriousdragon.smartindex.feature.home.state

import androidx.paging.LoadState
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.feature.home.model.DateFilter
import com.kuriousdragon.smartindex.feature.home.model.ListRequest
import com.kuriousdragon.smartindex.feature.home.model.Tab

enum class ErrorKind { DAY, SEARCH }

enum class EmptyKind { EMPTY_DAY, EMPTY_TAB, NO_RESULTS, EMPTY_RANGE }

/** What the list area shows. */
sealed interface ListStatus {
    data object Hint : ListStatus

    data class TabLocked(val tab: Tab, val group: Group, val state: GroupState) : ListStatus

    data class Skeleton(val rows: Int) : ListStatus

    data class Error(val kind: ErrorKind) : ListStatus

    data class Empty(val kind: EmptyKind) : ListStatus

    data object Content : ListStatus
}

/**
 * Inputs of [resolveListStatus]. [refresh] is the Paging refresh state, [skeletonDue] is true once it has
 * been Loading for IndexConfig.SKELETON_DELAY_MS. [groups] gives the state shown on a locked tab.
 */
data class ListStatusInput(
    val request: ListRequest,
    val refresh: LoadState,
    val itemCount: Int,
    val skeletonDue: Boolean,
    val counts: Map<ItemType, Int>?,
    val mode: HomeMode,
    val text: String,
    val dateFilter: DateFilter,
    val tab: Tab,
    val groups: Map<Group, GroupState>,
)

private const val HOME_SKELETON_ROWS = 6
private const val SEARCH_SKELETON_ROWS = 5

/** The decision table of LLD §4.6; the first matching row wins. */
internal fun resolveListStatus(i: ListStatusInput): ListStatus {
    val home = i.mode == HomeMode.HOME
    val request = i.request
    return when {
        request is ListRequest.Hint -> ListStatus.Hint

        request is ListRequest.Locked ->
            ListStatus.TabLocked(request.tab, request.group, i.groups[request.group] ?: GroupState.DENIED)

        i.refresh is LoadState.Error -> ListStatus.Error(if (home) ErrorKind.DAY else ErrorKind.SEARCH)

        i.refresh is LoadState.Loading && i.skeletonDue ->
            ListStatus.Skeleton(if (home) HOME_SKELETON_ROWS else SEARCH_SKELETON_ROWS)

        i.itemCount > 0 -> ListStatus.Content

        i.refresh is LoadState.Loading -> ListStatus.Content

        home && (i.tab == Tab.ALL || i.counts?.values?.sum() == 0) -> ListStatus.Empty(EmptyKind.EMPTY_DAY)

        home -> ListStatus.Empty(EmptyKind.EMPTY_TAB)

        i.text.isBlank() -> ListStatus.Empty(EmptyKind.EMPTY_RANGE)

        else -> ListStatus.Empty(EmptyKind.NO_RESULTS)
    }
}
