package com.kuriousdragon.smartindex.source.telephony.testing

import androidx.collection.LongSet
import com.kuriousdragon.smartindex.core.model.IndexWriter
import com.kuriousdragon.smartindex.core.model.ItemRecord
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.RecordingLink
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceKey
import com.kuriousdragon.smartindex.core.model.SyncState

/** In-memory [IndexWriter] that records every call. */
class FakeIndexWriter : IndexWriter {
    val items: MutableMap<SourceKey, ItemRecord> = linkedMapOf()

    /** Batch size of every `upsert` call, in order. */
    val upsertCalls: MutableList<Int> = mutableListOf()

    /** Source and sorted present ids of every `reconcile` call. */
    val reconciles: MutableList<Pair<Source, LongArray>> = mutableListOf()
    val contactNames: MutableMap<String, String> = linkedMapOf()
    val setContactNamesCalls: MutableList<Map<String, String?>> = mutableListOf()
    val states: MutableMap<Source, SyncState> = linkedMapOf()

    /** Every `saveSyncState` argument, in order. */
    val savedStates: MutableList<SyncState> = mutableListOf()

    private var nextUpsertFailure: Throwable? = null

    /** The next `upsert` call throws [error] (once). */
    fun failNextUpsert(error: Throwable) {
        nextUpsertFailure = error
    }

    override suspend fun upsert(batch: List<ItemRecord>) {
        nextUpsertFailure?.let {
            nextUpsertFailure = null
            throw it
        }
        upsertCalls += batch.size
        batch.forEach { items[it.key] = it }
    }

    override suspend fun reconcile(source: Source, presentIds: LongSet): Int {
        val ids = LongArray(presentIds.size)
        var i = 0
        presentIds.forEach { ids[i++] = it }
        ids.sort()
        reconciles += source to ids
        val keep = ids.toHashSet()
        val gone = items.keys.filter { it.source == source && it.sourceId !in keep }
        gone.forEach { items.remove(it) }
        return gone.size
    }

    override suspend fun purge(source: Source, types: Set<ItemType>?): Int = 0

    override suspend fun replaceRecordingLinks(links: List<RecordingLink>) = Unit

    override suspend fun replaceAllRecordingLinks(links: List<RecordingLink>) = Unit

    override suspend fun setContactNames(names: Map<String, String?>) {
        setContactNamesCalls += names
        names.forEach { (key, name) -> if (name == null) contactNames.remove(key) else contactNames[key] = name }
    }

    override suspend fun syncState(source: Source): SyncState? = states[source]

    override suspend fun saveSyncState(s: SyncState) {
        savedStates += s
        states[s.source] = s
    }
}
