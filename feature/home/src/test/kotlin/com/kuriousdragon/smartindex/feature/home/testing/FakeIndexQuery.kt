package com.kuriousdragon.smartindex.feature.home.testing

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import com.kuriousdragon.smartindex.core.model.IndexQuery
import com.kuriousdragon.smartindex.core.model.IndexStats
import com.kuriousdragon.smartindex.core.model.ItemDetail
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.RecordingInputs
import com.kuriousdragon.smartindex.core.model.ResultItem
import com.kuriousdragon.smartindex.core.model.SearchQuery
import com.kuriousdragon.smartindex.feature.home.model.CountsKey
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory [IndexQuery]. Records every call, serves [PagingData.from] pages, can fail the next page or
 * counts request, and can hold the next search page open until a list is sent on [heldPage].
 */
internal class FakeIndexQuery : IndexQuery {
    val timelineCalls = mutableListOf<Pair<LocalDate, ItemType?>>()
    val searchCalls = mutableListOf<SearchQuery>()
    val countsCalls = mutableListOf<CountsKey>()

    var timelineItems: (LocalDate, ItemType?) -> List<ResultItem> = { _, _ -> emptyList() }
    var searchItems: (SearchQuery) -> List<ResultItem> = { emptyList() }
    var dayCounts: (LocalDate) -> Map<ItemType, Int> = { zeroCounts() }
    var queryCounts: (SearchQuery) -> Map<ItemType, Int> = { zeroCounts() }

    /** The next paging flow throws this when collected. */
    var failNext: Throwable? = null

    /** When set, served pages carry these source load states (refresh / append errors). */
    var loadStates: LoadStates? = null

    /**
     * When set, every timeline and search flow first emits an empty page in the Loading state and then waits
     * for the gate before emitting its items (what a real Pager does).
     */
    var gate: CompletableDeferred<Unit>? = null

    /** The next counts call throws this. */
    var failCountsNext: Throwable? = null

    /** When true every search flow waits for a list on [heldPage] before emitting. */
    var holdSearchPages: Boolean = false

    /** Items released to a held search page. */
    val heldPage = Channel<List<ResultItem>>(Channel.UNLIMITED)

    /** When set, counts calls suspend on it first (to test cancellation). */
    var countsGate: (suspend () -> Unit)? = null

    override fun timeline(day: LocalDate, type: ItemType?): Flow<PagingData<ResultItem>> {
        timelineCalls += day to type
        return flow {
            failNext?.let {
                failNext = null
                throw it
            }
            awaitGate()
            emit(PagingData.from(timelineItems(day, type), loadStates ?: SETTLED))
        }
    }

    override fun search(q: SearchQuery): Flow<PagingData<ResultItem>> {
        searchCalls += q
        return flow {
            failNext?.let {
                failNext = null
                throw it
            }
            awaitGate()
            val items = if (holdSearchPages) heldPage.receive() else searchItems(q)
            emit(PagingData.from(items, loadStates ?: SETTLED))
        }
    }

    override suspend fun counts(day: LocalDate): Map<ItemType, Int> {
        countsCalls += CountsKey.Day(day)
        return loadCounts { dayCounts(day) }
    }

    override suspend fun counts(q: SearchQuery): Map<ItemType, Int> {
        countsCalls += CountsKey.Query(q)
        return loadCounts { queryCounts(q) }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<PagingData<ResultItem>>.awaitGate() {
        val pending = gate ?: return
        emit(PagingData.from(emptyList(), LOADING))
        pending.await()
    }

    private suspend fun loadCounts(block: () -> Map<ItemType, Int>): Map<ItemType, Int> {
        countsGate?.invoke()
        failCountsNext?.let {
            failCountsNext = null
            throw it
        }
        return block()
    }

    /** Details served by [item]. */
    val details = mutableMapOf<Long, ItemDetail>()

    override suspend fun item(id: Long): ItemDetail? = details[id]

    override fun stats(): Flow<IndexStats> = flowOf()

    override suspend fun recordingCandidates(since: Long): RecordingInputs =
        RecordingInputs(emptyList(), emptyList(), emptyList())

    companion object {
        val LOADING = LoadStates(
            refresh = LoadState.Loading,
            prepend = LoadState.NotLoading(endOfPaginationReached = false),
            append = LoadState.NotLoading(endOfPaginationReached = false),
        )

        /** Complete, idle states: what a finished Pager load reports. */
        val SETTLED = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        )

        fun zeroCounts(): Map<ItemType, Int> = ItemType.entries.associateWith { 0 }
    }
}
