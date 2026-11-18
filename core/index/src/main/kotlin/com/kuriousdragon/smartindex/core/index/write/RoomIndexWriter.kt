package com.kuriousdragon.smartindex.core.index.write

import androidx.collection.LongSet
import androidx.room.useWriterConnection
import com.kuriousdragon.smartindex.core.index.BuildConfig
import com.kuriousdragon.smartindex.core.index.db.IndexDatabase
import com.kuriousdragon.smartindex.core.index.db.code
import com.kuriousdragon.smartindex.core.index.db.entity.RecordingLinkEntity
import com.kuriousdragon.smartindex.core.index.db.entity.SyncStateEntity
import com.kuriousdragon.smartindex.core.index.db.source
import com.kuriousdragon.smartindex.core.index.debug.StorageReporter
import com.kuriousdragon.smartindex.core.model.IndexConfig
import com.kuriousdragon.smartindex.core.model.IndexWriter
import com.kuriousdragon.smartindex.core.model.ItemRecord
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.PhoneNumberNormalizer
import com.kuriousdragon.smartindex.core.model.RecordingLink
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SyncState
import javax.inject.Inject

/** Room implementation of [IndexWriter] (LLD §5.1). */
class RoomIndexWriter @Inject constructor(
    private val db: IndexDatabase,
    numbers: PhoneNumberNormalizer,
    private val storage: StorageReporter,
) : IndexWriter {
    private val mapper = RecordMapper(numbers)
    private val derivation = Derivation(numbers)

    override suspend fun upsert(batch: List<ItemRecord>) {
        if (batch.isEmpty()) return
        val unique = batch.associateBy { it.key }.values.toList()
        val dao = db.writeDao()
        for (chunk in unique.chunked(IndexConfig.UPSERT_BATCH_SIZE)) {
            val started = System.nanoTime()
            val written = dao.writeChunk(chunk, mapper, derivation)
            if (BuildConfig.DEBUG) {
                val ms = (System.nanoTime() - started) / NANOS_PER_MS
                SiLog.d(TAG, "n=${chunk.size} written=$written skipped=${chunk.size - written} ms=$ms")
            }
        }
    }

    override suspend fun reconcile(source: Source, presentIds: LongSet): Int {
        val dao = db.maintenanceDao()
        val stale = dao.idsOf(source.code()).filter { !presentIds.contains(it.sourceId) }.map { it.id }
        var deleted = 0
        for (chunk in stale.chunked(DELETE_CHUNK)) deleted += dao.deleteItems(chunk)
        if (deleted > 0) db.useWriterConnection { c -> c.usePrepared("PRAGMA optimize") { it.step() } }
        storage.reportIfDebug()
        return deleted
    }

    override suspend fun purge(source: Source, types: Set<ItemType>?): Int {
        val dao = db.maintenanceDao()
        val codes = types?.map { it.code() }
        if (codes != null && codes.isEmpty()) return 0
        var total = 0
        while (true) {
            val n = if (codes == null) dao.purgeChunk(source.code()) else dao.purgeTypesChunk(source.code(), codes)
            if (n == 0) break
            total += n
        }
        return total
    }

    override suspend fun replaceRecordingLinks(links: List<RecordingLink>) = replaceLinks(links, all = false)

    override suspend fun replaceAllRecordingLinks(links: List<RecordingLink>) = replaceLinks(links, all = true)

    private suspend fun replaceLinks(links: List<RecordingLink>, all: Boolean) {
        require(links.map { it.callItemId }.toSet().size == links.size) { "duplicate call id in links" }
        require(links.map { it.recordingItemId }.toSet().size == links.size) { "duplicate recording id in links" }
        val entities = links.map {
            RecordingLinkEntity(it.callItemId, it.recordingItemId, it.score.toDouble(), it.method)
        }
        val skipped = db.maintenanceDao().replaceLinks(entities, all)
        if (skipped > 0) SiLog.d(TAG, "recording links skipped=$skipped")
    }

    override suspend fun setContactNames(names: Map<String, String?>) {
        val dao = db.maintenanceDao()
        // A blank name carries no information, so it is treated like null (removal).
        val wanted = names.filterKeys { it.isNotBlank() }.mapValues { (_, v) -> v?.takeIf { it.isNotBlank() } }
        for (keys in wanted.keys.chunked(DELETE_CHUNK)) {
            val cached = dao.names(keys).associate { it.numberNorm to it.name }
            val changes = keys.filter { wanted[it] != cached[it] }.associateWith { wanted[it] }
            if (changes.isNotEmpty()) dao.applyContactNames(changes, derivation)
        }
    }

    override suspend fun syncState(source: Source): SyncState? =
        db.writeDao().syncState(source.code())?.let {
            SyncState(source(it.source), it.cursor, it.storeVersion, it.lastFullReconcile, it.lastRun, it.lastError)
        }

    override suspend fun saveSyncState(s: SyncState) {
        db.writeDao().upsertSyncState(
            SyncStateEntity(s.source.code(), s.cursor, s.storeVersion, s.lastFullReconcile, s.lastRun, s.lastError),
        )
    }

    private companion object {
        const val DELETE_CHUNK = 500
        const val TAG = "Index"
        const val NANOS_PER_MS = 1_000_000L
    }
}
