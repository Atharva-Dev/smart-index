package com.kuriousdragon.smartindex.sync.fakes

import androidx.collection.LongSet
import com.kuriousdragon.smartindex.core.model.IndexWriter
import com.kuriousdragon.smartindex.core.model.ItemRecord
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.RecordingLink
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SyncState

/** In-memory [IndexWriter]: keeps sync state, records purge / save / link calls, other members are no-ops. */
class FakeIndexWriter : IndexWriter {
    val states = mutableMapOf<Source, SyncState>()
    val purges = mutableListOf<Pair<Source, Set<ItemType>?>>()
    val saves = mutableListOf<SyncState>()
    val linkCalls = mutableListOf<List<RecordingLink>>()
    val replaceAllCalls = mutableListOf<List<RecordingLink>>()
    var throwOnLinks: Boolean = false
    var throwOnPurge: Set<Source> = emptySet()
    var throwOnSave: Boolean = false

    override suspend fun upsert(batch: List<ItemRecord>) = Unit

    override suspend fun reconcile(source: Source, presentIds: LongSet): Int = 0

    override suspend fun purge(source: Source, types: Set<ItemType>?): Int {
        purges += source to types
        if (source in throwOnPurge) throw IllegalStateException("purge failed")
        return 0
    }

    override suspend fun replaceRecordingLinks(links: List<RecordingLink>) {
        if (throwOnLinks) throw IllegalStateException("links failed")
        linkCalls += links
    }

    override suspend fun replaceAllRecordingLinks(links: List<RecordingLink>) {
        if (throwOnLinks) throw IllegalStateException("links failed")
        replaceAllCalls += links
    }

    override suspend fun setContactNames(names: Map<String, String?>) = Unit

    override suspend fun syncState(source: Source): SyncState? = states[source]

    override suspend fun saveSyncState(s: SyncState) {
        if (throwOnSave) throw IllegalStateException("save failed")
        saves += s
        states[s.source] = s
    }
}
