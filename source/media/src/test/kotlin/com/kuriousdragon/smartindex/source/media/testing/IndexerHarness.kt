package com.kuriousdragon.smartindex.source.media.testing

import androidx.test.core.app.ApplicationProvider
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.ItemRecord
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceKey
import com.kuriousdragon.smartindex.source.media.MediaStoreIndexer
import com.kuriousdragon.smartindex.source.media.map.MediaRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/** Wires a [MediaStoreIndexer] to the fakes. */
internal class IndexerHarness(
    rows: List<MediaRow> = emptyList(),
    groups: Map<Group, GroupState> =
        mapOf(Group.PHOTOS_VIDEOS to GroupState.GRANTED, Group.FILES to GroupState.GRANTED),
    io: CoroutineDispatcher = UnconfinedTestDispatcher(),
) {
    val reader = FakeMediaStoreReader(rows)
    val writer = FakeIndexWriter()
    val permissions = FakePermissionService(groups)
    val clock = FakeClock()
    val indexer = MediaStoreIndexer(reader, writer, permissions, clock, ApplicationProvider.getApplicationContext(), io)

    fun item(id: Long): ItemRecord? = writer.items[SourceKey(Source.MEDIA_STORE, id)]

    /** Invariants of LLD §4.7 that hold for every run. */
    fun assertInvariants() {
        val items = writer.upserts.flatten()
        check(items.all { it.key.source == Source.MEDIA_STORE }) { "foreign source written" }
        check(items.all { it.body == null }) { "body must be null" }
        check(writer.batchViolations.isEmpty()) { "batch too large: ${writer.batchViolations}" }
        val firstReconcile = writer.calls.indexOf("reconcile")
        if (firstReconcile >= 0) check(reader.openedCursors == reader.closedCursors) { "cursor left open" }
    }
}
