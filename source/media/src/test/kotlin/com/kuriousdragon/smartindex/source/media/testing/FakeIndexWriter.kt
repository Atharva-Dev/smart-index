package com.kuriousdragon.smartindex.source.media.testing

import androidx.collection.LongSet
import com.kuriousdragon.smartindex.core.model.IndexConfig
import com.kuriousdragon.smartindex.core.model.IndexWriter
import com.kuriousdragon.smartindex.core.model.ItemRecord
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.RecordingLink
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceKey
import com.kuriousdragon.smartindex.core.model.SyncState

/** In-memory [IndexWriter]: skips equal sourceVersion, records call order, batch violations and failures. */
class FakeIndexWriter : IndexWriter {
    val items: MutableMap<SourceKey, ItemRecord> = linkedMapOf()

    /** Every upsert batch, in order. */
    val upserts: MutableList<List<ItemRecord>> = mutableListOf()

    /** Sorted present ids of every reconcile call. */
    val reconciles: MutableList<LongArray> = mutableListOf()
    val states: MutableMap<Source, SyncState> = linkedMapOf()
    val savedStates: MutableList<SyncState> = mutableListOf()

    /** "upsert", "reconcile", "saveSyncState" in call order. */
    val calls: MutableList<String> = mutableListOf()

    /** Batches larger than the allowed size (the store contract); tests assert this is empty. */
    val batchViolations: MutableList<Int> = mutableListOf()
    val purges: MutableList<Pair<Source, Set<ItemType>?>> = mutableListOf()
    private val upsertFailures = mutableMapOf<Int, Throwable>()
    private var upsertCount = 0

    val upsertSizes: List<Int> get() = upserts.map { it.size }

    /** The n-th upsert call (1-based) throws [t]. */
    fun failOnUpsert(n: Int, t: Throwable) {
        upsertFailures[n] = t
    }

    override suspend fun upsert(batch: List<ItemRecord>) {
        calls += "upsert"
        upsertCount++
        upsertFailures[upsertCount]?.let { throw it }
        if (batch.size > IndexConfig.UPSERT_BATCH_SIZE) batchViolations += batch.size
        upserts += batch
        batch.forEach { r ->
            val old = items[r.key]
            if (old == null || old.sourceVersion != r.sourceVersion) items[r.key] = r
        }
    }

    override suspend fun reconcile(source: Source, presentIds: LongSet): Int {
        calls += "reconcile"
        val ids = LongArray(presentIds.size)
        var i = 0
        presentIds.forEach { ids[i++] = it }
        ids.sort()
        reconciles += ids
        val keep = ids.toHashSet()
        val gone = items.keys.filter { it.source == source && it.sourceId !in keep }
        gone.forEach { items.remove(it) }
        return gone.size
    }

    override suspend fun purge(source: Source, types: Set<ItemType>?): Int {
        purges += source to types
        return 0
    }

    override suspend fun replaceRecordingLinks(links: List<RecordingLink>) = Unit

    override suspend fun replaceAllRecordingLinks(links: List<RecordingLink>) = Unit

    override suspend fun setContactNames(names: Map<String, String?>) = Unit

    override suspend fun syncState(source: Source): SyncState? = states[source]

    override suspend fun saveSyncState(s: SyncState) {
        calls += "saveSyncState"
        savedStates += s
        states[s.source] = s
    }
}
