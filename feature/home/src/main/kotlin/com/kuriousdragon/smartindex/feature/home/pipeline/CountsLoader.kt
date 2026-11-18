package com.kuriousdragon.smartindex.feature.home.pipeline

import com.kuriousdragon.smartindex.core.model.IndexQuery
import com.kuriousdragon.smartindex.core.model.IndexStatus
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.feature.home.model.Commit
import com.kuriousdragon.smartindex.feature.home.model.CountsKey
import com.kuriousdragon.smartindex.feature.home.model.HomeConfig
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withTimeoutOrNull

/** Counts for the commit [commitId]; null = unknown (hide the badges). */
data class CountsResult(val commitId: Int, val counts: Map<ItemType, Int>?)

/** Loads the per-tab counts for each commit (LLD §3.5). */
internal class CountsLoader(private val index: IndexQuery) {
    /**
     * Emits null when the key differs from the previous one (hide badges), then the counts. Search keys wait
     * for the commit's first page ([settled] emits the settled commit id) for at most
     * [HomeConfig.COUNTS_WAIT_MS] ([HomeConfig.SHORT_COUNTS_WAIT_MS] for a 1-2 character query). A tick for the same key recomputes without emitting null first.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun counts(
        commits: Flow<Pair<Commit, CountsKey?>>,
        settled: Flow<Int>,
        ticks: Flow<Unit>,
    ): Flow<CountsResult> = flow {
        var previous: CountsKey? = null
        emitAll(
            commits.transformLatest { (commit, key) ->
                if (key == null) {
                    previous = null
                    emit(CountsResult(commit.id, null))
                    return@transformLatest
                }
                if (key != previous) {
                    previous = key
                    emit(CountsResult(commit.id, null))
                }
                if (key is CountsKey.Query) {
                    val wait = if (key.isShort) HomeConfig.SHORT_COUNTS_WAIT_MS else HomeConfig.COUNTS_WAIT_MS
                    withTimeoutOrNull(wait) { settled.first { it == commit.id } }
                }
                emitAll(merge(flowOf(Unit), ticks).mapLatest { load(commit, key) })
            },
        )
    }

    private suspend fun load(commit: Commit, key: CountsKey): CountsResult = try {
        val counts = when (key) {
            is CountsKey.Day -> index.counts(key.day)
            is CountsKey.Query -> index.counts(key.query)
        }
        CountsResult(commit.id, counts)
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") t: Throwable,
    ) {
        SiLog.w("Home", "counts failed", t)
        CountsResult(commit.id, null)
    }
}

/**
 * Leading edge, at most one tick per second while the indexed total changes (so at most one counts
 * recompute per second during indexing).
 */
internal fun indexTicks(status: Flow<IndexStatus>): Flow<Unit> =
    status.map { it.stats?.totalItems }
        .distinctUntilChanged()
        .conflate()
        .transform {
            emit(Unit)
            delay(TICK_INTERVAL_MS)
        }

private const val TICK_INTERVAL_MS = 1_000L
