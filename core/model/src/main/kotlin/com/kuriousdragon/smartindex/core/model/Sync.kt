package com.kuriousdragon.smartindex.core.model

import kotlinx.coroutines.flow.StateFlow

// Indexing orchestration contracts (HLD §6.4, §6.5).
// Owner: "LLD — Indexing orchestration & scheduling" (SIDX-12).
// Implemented by :sync (IndexCoordinator, DefaultIndexControl), :source:media and :source:telephony
// (SourceIndexer) and :source:recordings (RecordingMatcher).

/** How much work one [SourceIndexer.run] does. */
enum class Mode {
    /** Read every row of the source, upsert it, then reconcile deletions against the full ID set. */
    FULL,

    /** Read only rows changed since the source's cursor in [SyncState]; reconcile deletions by ID set. */
    INCREMENTAL,
}

/**
 * Receives progress from one running [SourceIndexer]. The coordinator gives every indexer its own sink.
 * Implementations are thread-safe, never throw and may be called from any thread.
 */
fun interface ProgressSink {
    /**
     * Reports absolute progress of this indexer's current run.
     *
     * @param done rows processed so far in this run (written + skipped), ≥ 0.
     * @param total rows expected in this run (for example the provider cursor's count), or 0 while unknown.
     *
     * Call it at least once per upsert batch and once at the end with `done == total`.
     */
    fun report(done: Int, total: Int)

    companion object {
        /** A sink that ignores every report (tests and tools). */
        val NONE: ProgressSink = ProgressSink { _, _ -> }
    }
}

/**
 * Mirrors one or more [Source]s of an Android provider into the index (HLD §6.4).
 *
 * Rules every implementation follows (LLD — Indexing orchestration & scheduling §3.3):
 * - Owns `cursor`, `storeVersion` and `lastFullReconcile` of its sources' [SyncState]: reads them with
 *   `IndexWriter.syncState(source)` and writes them with `IndexWriter.saveSyncState`, always copying
 *   `lastRun` and `lastError` unchanged from the previous state. It advances the cursor only for work that
 *   is durably written (checkpoints are allowed) and never on a failed source. `lastFullReconcile` = when
 *   it last completed a reconcile against the source's full ID set.
 * - Never writes `lastRun` / `lastError`: the coordinator sets them from [RunResult] after `run` returns.
 * - Indexes only item types whose [Group] is usable right now (`TypeGating.usableGroups(PermissionService.state.value)`),
 *   even if another permission would expose more (ADR-008).
 * - Runs FULL when asked, and may escalate [Mode.INCREMENTAL] to [Mode.FULL] (no state, MediaStore version
 *   changed); it reports the mode it actually used in [SourceRunResult.mode].
 * - Upserts in batches of at most [IndexConfig.UPSERT_BATCH_SIZE]; every write is idempotent.
 * - Never throws except `CancellationException`: a bad row is skipped and counted, a failing source is
 *   reported with [SourceRunResult.error] while its other sources continue (NFR-010).
 * - Checks for cancellation at least once per batch and rethrows `CancellationException`.
 */
interface SourceIndexer {
    /** The sources this indexer writes (it is their only writer, HLD §5.2). Never empty. */
    val sources: Set<Source>

    /** Groups that can make this indexer useful; the coordinator runs it when **any** of them is usable. */
    val requires: Set<Group>

    /** Runs one pass in [mode], reporting progress to [progress]. Returns one result per source it ran
     *  (v1.0.0 indexers have exactly one source each). */
    suspend fun run(mode: Mode, progress: ProgressSink): RunResult
}

/** Outcome of one [SourceIndexer.run]. Holds at most one [SourceRunResult] per [Source]. */
data class RunResult(val sources: List<SourceRunResult>) {
    /** Results whose source failed in this run. */
    val failed: List<SourceRunResult> get() = sources.filter { it.error != null }

    companion object {
        /** Every source in [sources] failed with the same [error] code. */
        fun failure(sources: Set<Source>, mode: Mode, error: String): RunResult =
            RunResult(sources.map { SourceRunResult.failed(it, mode, error) })
    }
}

/**
 * Outcome for one [source]. The coordinator stores [error] in `sync_state.last_error` and the run time in
 * `sync_state.last_run`; the cursor is persisted by the indexer itself (see [SourceIndexer]).
 *
 * @property mode the mode actually used (after any escalation to FULL).
 * @property written rows inserted or changed.
 * @property skipped bad or unreadable rows that were skipped.
 * @property deleted rows removed by reconcile.
 * @property error null on success, else a short content-free code: [SyncErrors.code] or an indexer's own
 *   `"<source>:<reason>:<ExceptionClass>"` form. Never a message, path, number or name.
 */
data class SourceRunResult(
    val source: Source,
    val mode: Mode,
    val written: Int = 0,
    val skipped: Int = 0,
    val deleted: Int = 0,
    val error: String? = null,
) {
    /** True when this source finished without error. */
    val succeeded: Boolean get() = error == null

    /** True when this run wrote or deleted at least one row of this source. */
    val changed: Boolean get() = written > 0 || deleted > 0

    companion object {
        /** A failed result. */
        fun failed(source: Source, mode: Mode, error: String): SourceRunResult =
            SourceRunResult(source = source, mode = mode, error = error)
    }
}

/** Content-free error codes stored in `sync_state.last_error` and [SourceRunResult.error]. */
object SyncErrors {
    /** The device or the index database ran out of space. */
    const val STORAGE_FULL: String = "STORAGE_FULL"

    /**
     * Maps [t] to a code: [STORAGE_FULL] when it (or a cause) means "disk full", else the exception's
     * simple class name (for example `SecurityException`). The message is inspected but never returned.
     */
    fun code(t: Throwable): String =
        if (isStorageFull(t)) STORAGE_FULL else t.javaClass.simpleName.ifEmpty { "Throwable" }

    /** True when a stored error [code] means "storage full" (also inside an indexer's composite code). */
    fun isStorageFullCode(code: String?): Boolean =
        code != null && (code.contains(STORAGE_FULL, ignoreCase = true) || code.contains("SQLiteFullException"))

    /** True when [t] or one of its first 8 causes signals a full disk or database. */
    fun isStorageFull(t: Throwable): Boolean =
        generateSequence(t) { it.cause }.take(8).any { e ->
            e.javaClass.simpleName == "SQLiteFullException" ||
                e.message?.let { m -> "SQLITE_FULL" in m || "ENOSPC" in m || "No space left" in m } == true
        }
}

/** Links Samsung call recordings to calls (HLD §6.4, ADR-010). Pure: it never writes the store. */
interface RecordingMatcher {
    /** Returns the one-to-one links for [inputs]; the coordinator writes them with `IndexWriter.replaceRecordingLinks`. */
    suspend fun match(inputs: RecordingInputs): List<RecordingLink>
}

/** Type gating (HLD §7, ADR-008): which onboarding [Group] allows which [ItemType] and [Source]. */
object TypeGating {
    /** The group whose grant allows indexing items of [type]. */
    fun groupOf(type: ItemType): Group = when (type) {
        ItemType.PHOTO, ItemType.VIDEO -> Group.PHOTOS_VIDEOS
        ItemType.DOCUMENT, ItemType.OTHER_FILE, ItemType.RECORDING -> Group.FILES
        ItemType.CALL -> Group.CALL_LOG
        ItemType.MESSAGE -> Group.MESSAGES
    }

    /** Item types allowed by [group]; empty for [Group.CONTACTS] (it only adds names). */
    fun typesOf(group: Group): Set<ItemType> =
        ItemType.entries.filterTo(mutableSetOf()) { groupOf(it) == group }

    /** Groups that can each allow some items of [source]. */
    fun groupsOf(source: Source): Set<Group> = when (source) {
        Source.MEDIA_STORE -> setOf(Group.PHOTOS_VIDEOS, Group.FILES)
        Source.CALL_LOG -> setOf(Group.CALL_LOG)
        Source.SMS, Source.MMS -> setOf(Group.MESSAGES)
    }

    /** Sources that [group] can allow; empty for [Group.CONTACTS]. */
    fun sourcesOf(group: Group): Set<Source> =
        Source.entries.filterTo(mutableSetOf()) { group in groupsOf(it) }

    /** GRANTED and PARTIAL (Android 14 "selected photos") are usable; everything else is not. */
    fun isUsable(state: GroupState?): Boolean =
        state == GroupState.GRANTED || state == GroupState.PARTIAL

    /** The usable groups in a `PermissionService.state` snapshot. */
    fun usableGroups(states: Map<Group, GroupState>): Set<Group> =
        states.filterValues { isUsable(it) }.keys.toSet()

    /** True when at least one group that allows [source] is in [usable]. */
    fun isUsable(source: Source, usable: Set<Group>): Boolean = groupsOf(source).any { it in usable }

    /** Item types allowed by the [usable] groups. */
    fun usableTypes(usable: Set<Group>): Set<ItemType> =
        ItemType.entries.filterTo(mutableSetOf()) { groupOf(it) in usable }
}

/** What the orchestrator is doing right now. */
enum class IndexPhase {
    /** No run is active. */
    IDLE,

    /** Checking storage and permissions, purging revoked types, planning. */
    PREPARING,

    /** Source indexers are reading providers and writing the index. */
    INDEXING,

    /** The recording matcher is linking recordings to calls. */
    LINKING,
}

/** The most important current problem, shown by the Index status error banner (Design Spec §6.8). */
data class IndexError(
    val kind: Kind,
    /** Sources affected; empty for [Kind.STORAGE_FULL] and [Kind.LINKING_FAILED]. */
    val sources: Set<Source> = emptySet(),
    /** When the error was recorded, epoch ms. */
    val atMs: Long,
) {
    enum class Kind {
        /** Free space is below [IndexConfig.MIN_FREE_BYTES] or a write failed with a full disk: "Indexing paused". */
        STORAGE_FULL,

        /** One or more sources failed in their last run; the others are indexed. */
        SOURCE_FAILED,

        /** Recording matching failed in the last run; calls and recordings are indexed but not linked. */
        LINKING_FAILED,
    }
}

/**
 * Snapshot of indexing for the UI (HLD §6.5, Design Spec §6.8, §6.12).
 *
 * UI mapping: Home/Search "Indexing your phone… {done} of {total}" banner and the Index status
 * "Indexing… {pct}%" banner when [isIndexing]; thin linear progress under the tabs when [isCatchUp];
 * "Up to date · Last indexed {lastRun}" when [phase] is IDLE and [error] is null; the error banner when
 * [error] is set and not [isIndexing]. "Re-index everything" is disabled while [isRunning].
 */
data class IndexStatus(
    val phase: IndexPhase = IndexPhase.IDLE,
    /** FULL when at least one source runs FULL in the current run, INCREMENTAL otherwise; null when IDLE. */
    val mode: Mode? = null,
    /** Rows processed in the current run, summed over sources; 0 when IDLE. */
    val done: Int = 0,
    /** Rows expected in the current run, summed over sources; 0 while unknown or IDLE. */
    val total: Int = 0,
    /** Per-type item counts and recordings linked, from `IndexQuery.stats()`; null until the first emission. */
    val stats: IndexStats? = null,
    /** End of the last completed run that ran at least one source, epoch ms; null if never. */
    val lastRun: Long? = null,
    /** Current error, or null. */
    val error: IndexError? = null,
    /**
     * True only while an INCREMENTAL (silent catch-up) run has been active for at least
     * [IndexConfig.CATCH_UP_PROGRESS_DELAY_MS]; drives the thin progress bar (Design Spec §6.8).
     */
    val isCatchUp: Boolean = false,
) {
    /** A run is active. */
    val isRunning: Boolean get() = phase != IndexPhase.IDLE

    /** A FULL run (initial index, re-index, newly granted group) is active: show the indexing banners. */
    val isIndexing: Boolean get() = isRunning && mode == Mode.FULL

    /** Progress 0..100; 0 while [total] is unknown. */
    val percent: Int
        get() = if (total <= 0) 0 else ((done.toLong() * 100L) / total).toInt().coerceIn(0, 100)
}

/** Read side of orchestration for the UI (HLD §6.5). */
interface IndexStatusSource {
    /** Hot, never completes; starts IDLE with [IndexStatus.lastRun] restored from `sync_state`. */
    val status: StateFlow<IndexStatus>
}

/**
 * Write side of orchestration for the UI (HLD §6.5). All methods are main-safe, return immediately and
 * schedule durable work; calling them repeatedly is harmless (work is coalesced).
 */
interface IndexControl {
    /** Silent INCREMENTAL catch-up now (expedited). No-op until indexing was started once. */
    fun catchUpNow()

    /**
     * Onboarding "Start indexing" and Index status "Re-index everything": enables indexing, resets every
     * cursor while **keeping** rows, then runs FULL + reconcile for every usable source.
     */
    fun reindexAll()

    /** Re-check permissions now: purge revoked types, FULL for newly granted groups. */
    fun onPermissionsChanged()
}
