package com.kuriousdragon.smartindex.sync

import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.IndexConfig
import com.kuriousdragon.smartindex.core.model.IndexError
import com.kuriousdragon.smartindex.core.model.IndexPhase
import com.kuriousdragon.smartindex.core.model.IndexQuery
import com.kuriousdragon.smartindex.core.model.IndexStatus
import com.kuriousdragon.smartindex.core.model.IndexStatusSource
import com.kuriousdragon.smartindex.core.model.IndexWriter
import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.core.model.RecordingMatcher
import com.kuriousdragon.smartindex.core.model.RunResult
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceIndexer
import com.kuriousdragon.smartindex.core.model.SourceRunResult
import com.kuriousdragon.smartindex.core.model.SyncErrors
import com.kuriousdragon.smartindex.core.model.SyncState
import com.kuriousdragon.smartindex.core.model.TypeGating
import com.kuriousdragon.smartindex.sync.di.SyncIo
import com.kuriousdragon.smartindex.sync.di.SyncScope
import com.kuriousdragon.smartindex.sync.work.ArmPolicy
import com.kuriousdragon.smartindex.sync.work.SyncScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * One single-flight orchestration pass over all source indexers and the recording matcher, plus the
 * [IndexStatus] the UI shows (LLD — Indexing orchestration & scheduling §5.4, §5.6, §5.13, §5.14).
 *
 * Column ownership of `sync_state`: indexers write `cursor` / `storeVersion` / `lastFullReconcile`; this class
 * writes only `lastRun` and `lastError` (read-modify-write after `run` returned).
 */
@Singleton
class IndexCoordinator @Inject constructor(
    private val indexers: Set<@JvmSuppressWildcards SourceIndexer>,
    private val matcher: java.util.Optional<RecordingMatcher>,
    private val writer: IndexWriter,
    private val query: IndexQuery,
    private val gate: PermissionGate,
    private val prefs: SyncPrefs,
    private val scheduler: SyncScheduler,
    private val freeSpace: FreeSpace,
    private val clock: SyncClock,
    @SyncScope private val scope: CoroutineScope,
    @SyncIo private val io: CoroutineDispatcher,
) : SyncRunner, IndexStatusSource {

    private val _status = MutableStateFlow(IndexStatus())
    override val status: StateFlow<IndexStatus> = _status.asStateFlow()

    private val mutex = Mutex()
    private val aggregator = ProgressAggregator { d, t ->
        publish { if (phase == IndexPhase.INDEXING) copy(done = d, total = t) else this }
    }

    // In-memory error state; written inside the run mutex, read when the status is derived.
    @Volatile private var memoryStorageFull: Boolean = false

    @Volatile private var linkFailed: Boolean = false

    @Volatile private var failedInMemory: Set<Source> = emptySet()

    @Volatile private var lastUsable: Set<Group>? = null

    @Volatile private var statsJob: Job? = null

    private class Planned(val indexer: SourceIndexer, val mode: Mode, val active: Set<Source>)

    private class LinkOutcome(val matched: Boolean, val error: String?)

    init {
        scope.launch {
            try {
                restore()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                SiLog.w(TAG, "restore failed", t)
            }
        }
        startStats()
    }

    override suspend fun sync(reason: SyncReason): SyncOutcome = mutex.withLock {
        if (!prefs.enabled) return@withLock SyncOutcome(skipped = SkipReason.NOT_ENABLED)
        val startedAt = clock.now()
        SiLog.i(TAG, "sync start reason=$reason")
        publish { copy(phase = IndexPhase.PREPARING, mode = null, done = 0, total = 0, isCatchUp = false) }
        startStats()
        var catchUpTimer: Job? = null
        var ranAny = false
        try {
            if (freeSpace.availableBytes() < IndexConfig.MIN_FREE_BYTES) {
                memoryStorageFull = true
                SiLog.w(TAG, "skipped: storage low")
                return@withLock SyncOutcome(skipped = SkipReason.STORAGE_LOW)
            }
            memoryStorageFull = false
            val g = gate.prepare()
            lastUsable = g.usable
            val plan = plan(g.usable, startedAt)
            if (plan.isEmpty()) {
                SiLog.i(TAG, "skipped: nothing usable")
                return@withLock SyncOutcome(skipped = SkipReason.NOTHING_USABLE)
            }
            val overall = if (plan.any { it.mode == Mode.FULL }) Mode.FULL else Mode.INCREMENTAL
            aggregator.reset()
            failedInMemory = emptySet()
            publish { copy(phase = IndexPhase.INDEXING, mode = overall) }
            if (overall == Mode.INCREMENTAL) {
                catchUpTimer = scope.launch {
                    delay(IndexConfig.CATCH_UP_PROGRESS_DELAY_MS)
                    publish { if (phase != IndexPhase.IDLE) copy(isCatchUp = true) else this }
                }
            }
            val sem = Semaphore(IndexConfig.MAX_PARALLEL_INDEXERS)
            val results = coroutineScope {
                plan.mapIndexed { slot, p -> async(io) { sem.withPermit { runOne(p, slot) } } }
                    .awaitAll()
                    .flatten()
            }
            ranAny = true
            val link = maybeMatch(g.usable, overall, results, startedAt)
            scheduler.armTriggers(g.usable, if (g.changed) ArmPolicy.REPLACE else ArmPolicy.KEEP)
            SyncOutcome(results = results, matched = link.matched, linkError = link.error)
        } finally {
            catchUpTimer?.cancel()
            withContext(NonCancellable) {
                val end = clock.now()
                val error = deriveError(end)
                publish {
                    copy(
                        phase = IndexPhase.IDLE,
                        mode = null,
                        done = 0,
                        total = 0,
                        isCatchUp = false,
                        error = error,
                        lastRun = if (ranAny) end else lastRun,
                    )
                }
            }
        }
    }

    private suspend fun plan(usable: Set<Group>, now: Long): List<Planned> {
        val planned = buildList {
            for (indexer in indexers) {
                val active = indexer.sources.filterTo(linkedSetOf()) { TypeGating.isUsable(it, usable) }
                if (active.isEmpty() || indexer.requires.none { it in usable }) continue
                val full = active.any { fullNeeded(writer.syncState(it), now) }
                add(Planned(indexer, if (full) Mode.FULL else Mode.INCREMENTAL, active))
            }
        }
        return planned.sortedWith(
            compareBy<Planned> { if (it.mode == Mode.FULL) 0 else 1 }
                .thenBy { PLAN_ORDER.indexOf(it.indexer.sources.first()) },
        )
    }

    private fun fullNeeded(state: SyncState?, now: Long): Boolean {
        val reconciled = state?.lastFullReconcile ?: return true
        return now - reconciled > IndexConfig.FULL_RESYNC_INTERVAL_MS
    }

    private suspend fun runOne(p: Planned, slot: Int): List<SourceRunResult> {
        val began = clock.now()
        val result = try {
            p.indexer.run(p.mode, aggregator.sinkFor(slot))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            RunResult.failure(p.active, p.mode, SyncErrors.code(t))
        }
        val kept = LinkedHashMap<Source, SourceRunResult>()
        for (r in result.sources) {
            when {
                r.source !in p.active -> SiLog.d(TAG, "ignored result source=${r.source}")
                r.source in kept -> SiLog.w(TAG, "duplicate result source=${r.source}")
                else -> kept[r.source] = r
            }
        }
        for (s in p.active) {
            if (s !in kept) SiLog.w(TAG, "no result source=$s")
        }
        for ((s, r) in kept) {
            val now = clock.now()
            try {
                val cur = writer.syncState(s) ?: SyncState(s, 0, null, null, null, null)
                writer.saveSyncState(cur.copy(lastRun = now, lastError = r.error))
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                failedInMemory = failedInMemory + s
                SiLog.w(TAG, "save sync state failed source=$s code=${SyncErrors.code(t)}")
            }
            SiLog.d(
                TAG,
                "source $s mode=${r.mode} written=${r.written} skipped=${r.skipped} deleted=${r.deleted} " +
                    "ms=${now - began}",
            )
            if (r.error != null) SiLog.w(TAG, "source $s failed code=${r.error}")
        }
        return kept.values.toList()
    }

    private suspend fun maybeMatch(
        usable: Set<Group>,
        overall: Mode,
        results: List<SourceRunResult>,
        startedAt: Long,
    ): LinkOutcome {
        val lastMatch = prefs.lastMatchRun
        val needed = matcher.isPresent &&
            Group.CALL_LOG in usable && Group.FILES in usable &&
            (
                overall == Mode.FULL || lastMatch == null ||
                    results.any { it.succeeded && it.changed && it.source in MATCH_SOURCES }
                )
        if (!needed) return LinkOutcome(matched = false, error = null)
        publish { copy(phase = IndexPhase.LINKING) }
        return try {
            val since = if (overall == Mode.FULL || lastMatch == null) {
                0L
            } else {
                lastMatch - IndexConfig.RECORDING_MATCH_OVERLAP_MS
            }
            val links = matcher.get().match(query.recordingCandidates(since))
            if (since == 0L) writer.replaceAllRecordingLinks(links) else writer.replaceRecordingLinks(links)
            prefs.lastMatchRun = startedAt
            linkFailed = false
            LinkOutcome(matched = true, error = null)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            linkFailed = true
            val code = SyncErrors.code(t)
            SiLog.w(TAG, "linking failed code=$code")
            LinkOutcome(matched = false, error = code)
        }
    }

    private suspend fun deriveError(atMs: Long): IndexError? {
        val usable = lastUsable
        val sources = Source.entries.filter { usable == null || TypeGating.isUsable(it, usable) }
        val failed = linkedSetOf<Source>()
        var storageFull = memoryStorageFull
        for (s in sources) {
            val lastError = try {
                writer.syncState(s)?.lastError
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                SiLog.w(TAG, "read sync state failed source=$s", t)
                null
            }
            if (SyncErrors.isStorageFullCode(lastError)) storageFull = true
            if (lastError != null || s in failedInMemory) failed += s
        }
        return when {
            storageFull -> IndexError(IndexError.Kind.STORAGE_FULL, atMs = atMs)
            failed.isNotEmpty() -> IndexError(IndexError.Kind.SOURCE_FAILED, failed, atMs)
            linkFailed -> IndexError(IndexError.Kind.LINKING_FAILED, atMs = atMs)
            else -> null
        }
    }

    private suspend fun restore() {
        val lastRun = Source.entries.mapNotNull { writer.syncState(it)?.lastRun }.maxOrNull()
        val error = deriveError(clock.now())
        publish { copy(lastRun = this.lastRun ?: lastRun, error = this.error ?: error) }
    }

    private fun startStats() {
        if (statsJob?.isActive == true) return
        statsJob = scope.launch {
            try {
                query.stats().collect { s -> publish { copy(stats = s) } }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                SiLog.w(TAG, "stats flow failed", t)
            }
        }
    }

    private inline fun publish(crossinline block: IndexStatus.() -> IndexStatus) {
        _status.update { it.block() }
    }

    private companion object {
        const val TAG = "Sync"
        val PLAN_ORDER = listOf(Source.SMS, Source.MEDIA_STORE, Source.MMS, Source.CALL_LOG)
        val MATCH_SOURCES = setOf(Source.CALL_LOG, Source.MEDIA_STORE)
    }
}
