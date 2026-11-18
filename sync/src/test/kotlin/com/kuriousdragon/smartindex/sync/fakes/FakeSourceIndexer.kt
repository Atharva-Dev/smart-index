package com.kuriousdragon.smartindex.sync.fakes

import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.core.model.ProgressSink
import com.kuriousdragon.smartindex.core.model.RunResult
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceIndexer
import com.kuriousdragon.smartindex.core.model.SourceRunResult
import com.kuriousdragon.smartindex.core.model.SyncState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

/** Shared across fake indexers: how many ran at once and in which order they started. */
class RunTracker {
    var current = 0
    var max = 0
    val startOrder = mutableListOf<Source>()
}

/** Scripted [SourceIndexer]. Default result: every source succeeded with one row written in the given mode. */
class FakeSourceIndexer(
    override val sources: Set<Source>,
    override val requires: Set<Group>,
    private val now: () -> Long = { 0L },
    private val tracker: RunTracker = RunTracker(),
) : SourceIndexer {
    /** Scripted result, by the mode run. */
    var result: ((Mode) -> RunResult)? = null
    var throwable: Throwable? = null
    var gate: CompletableDeferred<Unit>? = null
    var delayMs: Long = 0L

    /** When set, saves cursor / storeVersion / lastFullReconcile through this writer like a real indexer. */
    var writesCursor: FakeIndexWriter? = null
    var reportProgress: Pair<Int, Int>? = null
    var lastSink: ProgressSink? = null

    val modes = mutableListOf<Mode>()
    val starts = mutableListOf<Long>()
    val ends = mutableListOf<Long>()

    val calls: Int get() = modes.size

    override suspend fun run(mode: Mode, progress: ProgressSink): RunResult {
        modes += mode
        starts += now()
        lastSink = progress
        tracker.startOrder += sources.first()
        tracker.current++
        tracker.max = maxOf(tracker.max, tracker.current)
        try {
            reportProgress?.let { (d, t) -> progress.report(d, t) }
            gate?.await()
            if (delayMs > 0) delay(delayMs)
            throwable?.let { throw it }
            writesCursor?.let { w ->
                for (s in sources) {
                    val cur = w.syncState(s) ?: SyncState(s, 0, null, null, null, null)
                    w.saveSyncState(
                        cur.copy(cursor = CURSOR, storeVersion = STORE_VERSION, lastFullReconcile = RECONCILED),
                    )
                }
            }
            return result?.invoke(mode) ?: RunResult(sources.map { SourceRunResult(it, mode, written = 1) })
        } finally {
            tracker.current--
            ends += now()
        }
    }

    companion object {
        const val CURSOR = 42L
        const val STORE_VERSION = "v-test"
        const val RECONCILED = 1_700_000_000_000L
    }
}
