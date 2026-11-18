package com.kuriousdragon.smartindex.source.telephony.common

import androidx.collection.MutableLongSet
import com.kuriousdragon.smartindex.core.model.IndexConfig
import com.kuriousdragon.smartindex.core.model.ItemRecord
import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.core.model.ProgressSink
import com.kuriousdragon.smartindex.core.model.RunResult
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceIndexer
import com.kuriousdragon.smartindex.core.model.SyncState
import com.kuriousdragon.smartindex.source.telephony.TelephonyConfig
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Template shared by the three telephony indexers (LLD — Telephony indexer §4.7, §5, error table §3.5).
 * A subclass supplies the queries, the row reader and the row → [ItemRecord] mapping; this class owns
 * FULL / INCREMENTAL, batching, checkpoints, reconcile, sync state, progress, logging and error mapping.
 *
 * `run` never throws except [CancellationException]. It runs on `deps.dispatcher`.
 */
abstract class ProviderSourceIndexer<R : ProviderRow>(private val deps: TelephonyDeps) : SourceIndexer {
    protected abstract val source: Source
    protected abstract val tag: String
    protected abstract val queries: SourceQueries
    protected abstract val reader: RowReader<R>

    /** Map one batch (≤ 500 rows). Return only the records to upsert; call stats.skip(reason) for each dropped row. */
    protected abstract suspend fun map(batch: List<R>, stats: RunStatsBuilder): List<ItemRecord>

    /** Per-run reset hook (MMS: drop thread cache, reset addr budget). */
    protected open fun onRunStart() {}

    override val sources: Set<Source> get() = setOf(source)

    /** Thrown inside [run] when a provider returns a null cursor. */
    private class ProviderUnavailableException : RuntimeException()

    /** Progress and cursor bookkeeping shared by the scans of one run. */
    private class Scan(val state: SyncState?, val progress: ProgressSink, startCursor: Long) {
        var done = 0
        var total = 0
        var maxId = startCursor
        var batches = 0
    }

    final override suspend fun run(mode: Mode, progress: ProgressSink): RunResult = withContext(deps.dispatcher) {
        val startedAt = deps.clock.elapsedMs()
        var stats = RunStatsBuilder(source, mode)
        try {
            val state = deps.writer.syncState(source)
            val effective = if (mode == Mode.FULL || state == null) Mode.FULL else Mode.INCREMENTAL
            stats = RunStatsBuilder(source, effective)
            deps.country.refresh()
            deps.display.clear()
            onRunStart()
            deps.contacts.ensureFresh(full = mode == Mode.FULL) // never fails the run
            // FULL ignores the stored cursor, so its checkpoints start from 0.
            val scan = Scan(state, progress, if (effective == Mode.FULL) 0L else state?.cursor ?: 0L)
            if (effective == Mode.FULL) runFull(scan, stats) else runIncremental(scan, stats)
            progress.report(scan.done, scan.total.coerceAtLeast(scan.done))
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            stats.fail(RunError.PERMISSION_REVOKED, e)
        } catch (e: ProviderUnavailableException) {
            stats.fail(RunError.PROVIDER_UNAVAILABLE, e)
        } catch (e: Exception) {
            stats.fail(RunError.PROVIDER_ERROR, e)
        }
        val result = stats.build(deps.clock.elapsedMs() - startedAt)
        log(result)
        result.toRunResult()
    }

    private suspend fun runFull(scan: Scan, stats: RunStatsBuilder) {
        val ids = MutableLongSet()
        scanRows(queries.full(), scan, stats, ids = ids, checkpoint = true)
        stats.deleted = deps.writer.reconcile(source, ids)
        saveState(scan.state, scan.maxId, lastFullReconcile = deps.clock.nowMs())
    }

    private suspend fun runIncremental(scan: Scan, stats: RunStatsBuilder) {
        val cursor = scan.state?.cursor ?: 0L
        scanRows(queries.newRows(cursor), scan, stats, ids = null, checkpoint = false)
        scanRows(
            queries.recent(deps.clock.nowMs() - TelephonyConfig.RECENT_WINDOW_MS, cursor),
            scan,
            stats,
            ids = null,
            checkpoint = false,
        )
        val ids = MutableLongSet()
        scanIds(queries.idScan(), ids)
        stats.deleted = deps.writer.reconcile(source, ids)
        saveState(scan.state, scan.maxId, lastFullReconcile = deps.clock.nowMs())
    }

    /** Streams [spec] in batches of the upsert-batch size: read → map → upsert → progress → checkpoint. */
    private suspend fun scanRows(
        spec: QuerySpec,
        scan: Scan,
        stats: RunStatsBuilder,
        ids: MutableLongSet?,
        checkpoint: Boolean,
    ) {
        val cursor = deps.providers.query(spec) ?: throw ProviderUnavailableException()
        cursor.use { c ->
            scan.total += c.count
            val rowReader = CursorReader(c)
            val batch = ArrayList<R>(IndexConfig.UPSERT_BATCH_SIZE)
            while (c.moveToNext()) {
                val row = reader.read(rowReader)
                stats.read++
                if (row == null) {
                    stats.skip("no_id")
                    scan.done++
                    continue
                }
                ids?.add(row.id)
                if (row.id > scan.maxId) scan.maxId = row.id
                batch.add(row)
                if (batch.size >= IndexConfig.UPSERT_BATCH_SIZE) {
                    flush(batch, scan, stats, checkpoint)
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) flush(batch, scan, stats, checkpoint)
        }
    }

    private suspend fun flush(batch: List<R>, scan: Scan, stats: RunStatsBuilder, checkpoint: Boolean) {
        currentCoroutineContext().ensureActive()
        val records = map(batch, stats)
        if (records.isNotEmpty()) deps.writer.upsert(records)
        stats.written += records.size
        scan.done += batch.size
        scan.batches++
        scan.progress.report(scan.done, scan.total.coerceAtLeast(scan.done))
        if (checkpoint && scan.batches % TelephonyConfig.CHECKPOINT_EVERY_BATCHES == 0) {
            saveState(scan.state, scan.maxId, lastFullReconcile = scan.state?.lastFullReconcile)
        }
    }

    private suspend fun scanIds(spec: QuerySpec, ids: MutableLongSet) {
        val cursor = deps.providers.query(spec) ?: throw ProviderUnavailableException()
        cursor.use { c ->
            val rowReader = CursorReader(c)
            var n = 0
            while (c.moveToNext()) {
                rowReader.long("_id")?.takeIf { it > 0 }?.let { ids.add(it) }
                if (++n % ID_SCAN_CANCEL_CHECK == 0) currentCoroutineContext().ensureActive()
            }
        }
    }

    /** This module owns `cursor` and `lastFullReconcile`; `lastRun` / `lastError` belong to orchestration. */
    private suspend fun saveState(previous: SyncState?, cursor: Long, lastFullReconcile: Long?) {
        deps.writer.saveSyncState(
            SyncState(
                source = source,
                cursor = cursor,
                storeVersion = null,
                lastFullReconcile = lastFullReconcile,
                lastRun = previous?.lastRun,
                lastError = previous?.lastError,
            ),
        )
    }

    /** Counts, modes, durations and exception class names only (LLD §6.2). */
    private fun log(s: TelephonyRunStats) {
        if (s.error == null) {
            SiLog.i(
                tag,
                "run mode=${s.mode} read=${s.read} written=${s.written} skipped=${s.skipped} notes=${s.notes} ms=${s.durationMs}",
            )
        } else {
            SiLog.w(tag, "run failed mode=${s.mode} error=${s.error} class=${s.errorClass} read=${s.read}")
        }
    }

    private companion object {
        const val ID_SCAN_CANCEL_CHECK = 1_000
    }
}
