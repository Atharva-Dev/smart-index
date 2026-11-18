package com.kuriousdragon.smartindex.core.index.query

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.kuriousdragon.smartindex.core.index.BuildConfig
import com.kuriousdragon.smartindex.core.index.db.IndexDatabase
import com.kuriousdragon.smartindex.core.model.PerfTrace
import com.kuriousdragon.smartindex.core.model.ResultItem
import com.kuriousdragon.smartindex.core.model.SiLog
import kotlinx.coroutines.CancellationException

/** Offset paging over a [SearchPlan] (LLD §5.7): narrow key query, then one hydrate query for the page. */
class IndexPagingSource(
    private val plan: SearchPlan,
    private val db: IndexDatabase,
    signal: IndexChangeSignal,
) : PagingSource<Int, ResultItem>() {
    init {
        val unregister = signal.register { invalidate() }
        registerInvalidatedCallback { unregister() }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ResultItem> =
        PerfTrace.traced(PerfTrace.STORE_PAGE) {
            val start = System.nanoTime()
            try {
                val key = params.key ?: 0
                val (offset, limit) =
                    when (params) {
                        is LoadParams.Prepend -> {
                            val from = maxOf(0, key - params.loadSize)
                            from to (key - from)
                        }

                        else -> key to params.loadSize
                    }
                val keys = db.searchDao().keys(SearchSql.page(plan, limit, offset))
                val rows = db.readDao().rows(keys.map { it.id }).associateBy { it.id }
                val q = plan.foldedQuery
                val items = keys.mapNotNull { k -> rows[k.id]?.let { ResultMapper.map(it, k.ts, q) } }
                if (BuildConfig.DEBUG) {
                    SiLog.d(
                        "Index",
                        "plan=${plan.letter()} rows=${items.size} ms=${(System.nanoTime() - start) / 1_000_000}",
                    )
                }
                if (invalid) {
                    LoadResult.Invalid()
                } else {
                    LoadResult.Page(
                        data = items,
                        prevKey = offset.takeIf { it > 0 },
                        nextKey = (offset + keys.size).takeIf { keys.size == limit },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SiLog.w("Index", "page load failed plan=${plan.letter()}", e)
                LoadResult.Error(e)
            }
        }
    override fun getRefreshKey(state: PagingState<Int, ResultItem>): Int? =
        state.anchorPosition?.let { maxOf(0, it - REFRESH_BACKOFF) }

    private companion object {
        const val REFRESH_BACKOFF = 25
    }
}

/** The plan letter of LLD §5.6, for debug timing logs. */
internal fun SearchPlan.letter(): Char =
    when (this) {
        is SearchPlan.Fts -> if (range == null) 'A' else 'B'
        is SearchPlan.Short -> if (range == null) 'C' else 'D'
        is SearchPlan.Browse -> 'E'
        SearchPlan.Empty -> '-'
    }
