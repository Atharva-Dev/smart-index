package com.kuriousdragon.smartindex.source.media

import android.content.Context
import android.database.Cursor
import android.os.CancellationSignal
import androidx.collection.MutableLongSet
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.IndexConfig
import com.kuriousdragon.smartindex.core.model.IndexWriter
import com.kuriousdragon.smartindex.core.model.ItemRecord
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.core.model.PermissionService
import com.kuriousdragon.smartindex.core.model.ProgressSink
import com.kuriousdragon.smartindex.core.model.RunResult
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceIndexer
import com.kuriousdragon.smartindex.core.model.SyncState
import com.kuriousdragon.smartindex.core.model.TypeGating
import com.kuriousdragon.smartindex.source.media.di.MediaIo
import com.kuriousdragon.smartindex.source.media.map.Classification
import com.kuriousdragon.smartindex.source.media.map.MediaClassifier
import com.kuriousdragon.smartindex.source.media.map.MediaMapper
import com.kuriousdragon.smartindex.source.media.map.MediaRow
import com.kuriousdragon.smartindex.source.media.run.MediaClock
import com.kuriousdragon.smartindex.source.media.run.MediaRunError
import com.kuriousdragon.smartindex.source.media.run.MediaRunStats
import com.kuriousdragon.smartindex.source.media.store.MediaCursorReader
import com.kuriousdragon.smartindex.source.media.store.MediaQueries
import com.kuriousdragon.smartindex.source.media.store.MediaStoreReader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

/**
 * The one [SourceIndexer] for [Source.MEDIA_STORE]: mirrors MediaStore Files of all external volumes into the
 * index, FULL and INCREMENTAL, with deletions by ID-set reconcile (LLD — Media & file indexer §3.3.5, §5.1–5.4).
 */
class MediaStoreIndexer @Inject constructor(
    private val reader: MediaStoreReader,
    private val writer: IndexWriter,
    private val permissions: PermissionService,
    private val clock: MediaClock,
    @param:ApplicationContext context: Context,
    @param:MediaIo private val io: CoroutineDispatcher,
) : SourceIndexer {
    override val sources: Set<Source> = setOf(Source.MEDIA_STORE)
    override val requires: Set<Group> = TypeGating.groupsOf(Source.MEDIA_STORE)
    private val classifier = MediaClassifier(context.packageName)

    /** Test seam: how a cursor row becomes a [MediaRow]. A throw is a `bad_row`. */
    internal var rowDecoder: (MediaCursorReader, Cursor) -> MediaRow = { r, c -> r.row(c) }

    /** Test seam: the mapper. A throw is a `map_error`. */
    internal var mapper: (MediaRow, ItemType, Long) -> ItemRecord = MediaMapper::map

    override suspend fun run(mode: Mode, progress: ProgressSink): RunResult = withContext(io) {
        val states = permissions.state.value
        val types = TypeGating.usableTypes(TypeGating.usableGroups(states)) intersect MEDIA_TYPES
        if (types.isEmpty()) return@withContext RunResult(emptyList())
        val partial = states[Group.PHOTOS_VIDEOS] == GroupState.PARTIAL && ItemType.PHOTO in types
        Run(mode, types, partial, progress).execute()
    }

    private class NullCursorException : RuntimeException()

    private inner class Run(
        private val requested: Mode,
        private val types: Set<ItemType>,
        private val partial: Boolean,
        private val progress: ProgressSink,
    ) {
        private var stats = MediaRunStats(requested)
        private var stage = STAGE_QUERY
        private val batch = ArrayList<ItemRecord>(IndexConfig.UPSERT_BATCH_SIZE)
        private var lastBatchGen = 0L
        private var checkpoint: (suspend (Long) -> Unit)? = null

        @Suppress("TooGenericExceptionCaught")
        suspend fun execute(): RunResult {
            val started = clock.elapsedMs()
            try {
                val prev = writer.syncState(Source.MEDIA_STORE)
                val version = MediaQueries.storeVersion(reader)
                val full = requested == Mode.FULL || prev == null || prev.storeVersion != version
                stats = MediaRunStats(if (full) Mode.FULL else Mode.INCREMENTAL)
                if (full || prev == null) full(version) else incremental(prev, version)
            } catch (e: CancellationException) {
                throw e
            } catch (t: SecurityException) {
                stats.fail(MediaRunError.PERMISSION_REVOKED, t)
            } catch (_: NullCursorException) {
                stats.fail(MediaRunError.PROVIDER_UNAVAILABLE, null)
            } catch (t: Throwable) {
                stats.fail(if (stage == STAGE_WRITE) MediaRunError.WRITE_FAILED else MediaRunError.PROVIDER_ERROR, t)
            }
            log(clock.elapsedMs() - started)
            return stats.toRunResult()
        }

        private suspend fun full(version: String) {
            val present = MutableLongSet()
            var maxGen = 0L
            query(null, null, MediaQueries.SORT_FULL, MediaQueries.ROW_PROJECTION) { c, r ->
                stats.total = c.count
                progress.report(0, stats.total)
                forEachRow(c, r, present) { gen -> maxGen = maxOf(maxGen, gen) }
            }
            flush()
            stage = STAGE_WRITE
            stats.deleted = writer.reconcile(Source.MEDIA_STORE, present)
            save(maxGen, version, clock.nowMs())
            progress.report(stats.total, stats.total)
        }

        private suspend fun incremental(prev: SyncState, version: String) {
            val c0 = prev.cursor
            var maxGen = c0
            checkpoint = { gen -> save(maxOf(c0, gen - 1), version, prev.lastFullReconcile) }
            query(
                MediaQueries.incrementalSelection(partial),
                arrayOf(c0.toString()),
                MediaQueries.SORT_INCREMENTAL,
                MediaQueries.ROW_PROJECTION,
            ) { c, r ->
                stats.total = c.count
                progress.report(0, stats.total)
                forEachRow(c, r, null) { gen -> maxGen = maxOf(maxGen, gen) }
            }
            flush()
            val present = idPass()
            stage = STAGE_WRITE
            stats.deleted = writer.reconcile(Source.MEDIA_STORE, present)
            save(maxGen, version, prev.lastFullReconcile)
            progress.report(stats.total, stats.total)
        }

        private suspend fun idPass(): MutableLongSet {
            val present = MutableLongSet()
            query(null, null, MediaQueries.SORT_IDS, MediaQueries.ID_PROJECTION) { c, r ->
                while (c.moveToNext()) {
                    coroutineContext.ensureActive()
                    val row = try {
                        rowDecoder(r, c)
                    } catch (_: Exception) {
                        r.id(c)?.takeIf { it > 0 }?.let(present::add)
                        continue
                    }
                    val id = row.id
                    if (id == null || id <= 0) continue
                    val cl = classifier.classify(row)
                    if (cl is Classification.Typed && cl.type in types) present.add(id)
                }
            }
            return present
        }

        private suspend fun forEachRow(
            c: Cursor,
            r: MediaCursorReader,
            present: MutableLongSet?,
            onGen: (Long) -> Unit,
        ) {
            while (c.moveToNext()) {
                stats.read++
                val row = try {
                    rowDecoder(r, c)
                } catch (_: Exception) {
                    stats.skip("bad_row")
                    r.id(c)?.takeIf { it > 0 }?.let { present?.add(it) }
                    continue
                }
                onGen(row.generationModified ?: 0L)
                handleRow(row, present)
                if (batch.size >= IndexConfig.UPSERT_BATCH_SIZE) flush()
            }
        }

        private fun handleRow(row: MediaRow, present: MutableLongSet?) {
            val id = row.id
            if (id == null || id <= 0) {
                stats.skip("no_id")
                return
            }
            when (val cl = classifier.classify(row)) {
                is Classification.Excluded -> stats.exclude(cl.reason.name)

                is Classification.Typed -> {
                    if (cl.type !in types) {
                        stats.exclude("type_not_allowed")
                        return
                    }
                    present?.add(id)
                    @Suppress("TooGenericExceptionCaught")
                    try {
                        batch += mapper(row, cl.type, clock.nowMs())
                        lastBatchGen = row.generationModified ?: lastBatchGen
                    } catch (_: Exception) {
                        // The id stays in `present`, so the old row is kept.
                        stats.skip("map_error")
                    }
                }
            }
        }

        private suspend fun flush() {
            if (batch.isEmpty()) return
            coroutineContext.ensureActive()
            stage = STAGE_WRITE
            writer.upsert(batch.toList())
            stats.written += batch.size
            batch.clear()
            checkpoint?.invoke(lastBatchGen)
            stage = STAGE_QUERY
            coroutineContext.ensureActive()
            progress.report(stats.read, stats.total)
        }

        private suspend fun save(cursor: Long, version: String, lastFullReconcile: Long?) {
            stage = STAGE_WRITE
            val cur = writer.syncState(Source.MEDIA_STORE) ?: SyncState(Source.MEDIA_STORE, 0, null, null, null, null)
            writer.saveSyncState(
                cur.copy(cursor = cursor, storeVersion = version, lastFullReconcile = lastFullReconcile),
            )
        }

        /** One query with its own [CancellationSignal], cancelled when the coroutine completes abnormally. */
        private suspend fun query(
            selection: String?,
            args: Array<String>?,
            sort: String,
            projection: Array<String>,
            block: suspend (Cursor, MediaCursorReader) -> Unit,
        ) {
            val signal = CancellationSignal()
            val handle: DisposableHandle = coroutineContext.job.invokeOnCompletion { cause ->
                if (cause != null) signal.cancel()
            }
            try {
                val cursor = reader.query(MediaQueries.FILES_URI, projection, selection, args, sort, signal)
                    ?: throw NullCursorException()
                cursor.use { block(it, MediaCursorReader(it)) }
            } finally {
                if (coroutineContext.isActive) handle.dispose()
            }
        }

        private fun log(ms: Long) {
            val error = stats.errorCode()
            if (error != null) {
                SiLog.w(TAG, "run failed mode=${stats.mode} error=$error read=${stats.read}")
            } else {
                SiLog.i(
                    TAG,
                    "run mode=${stats.mode} requested=$requested types=${types.size} read=${stats.read} " +
                        "written=${stats.written} skipped=${stats.skipped} excluded=${stats.excluded} " +
                        "deleted=${stats.deleted} ms=$ms",
                )
            }
        }
    }

    internal companion object {
        val MEDIA_TYPES = setOf(
            ItemType.PHOTO,
            ItemType.VIDEO,
            ItemType.DOCUMENT,
            ItemType.OTHER_FILE,
            ItemType.RECORDING,
        )
        const val TAG = "Media"
        private const val STAGE_QUERY = "query"
        private const val STAGE_WRITE = "write"
    }
}
