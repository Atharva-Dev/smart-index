package com.kuriousdragon.smartindex.source.media

import android.provider.MediaStore.Files.FileColumns
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SyncState
import com.kuriousdragon.smartindex.source.media.testing.IndexerHarness
import com.kuriousdragon.smartindex.source.media.testing.RecordingProgressSink
import com.kuriousdragon.smartindex.source.media.testing.row
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaStoreIndexerGatingTest {
    private fun mixed() = listOf(
        row(id = 1),
        row(id = 2, name = "v.mp4", mime = "video/mp4", mediaType = FileColumns.MEDIA_TYPE_VIDEO),
        row(id = 3, name = "a.pdf", path = "Download/", mime = "application/pdf", mediaType = 0),
        row(id = 4, name = "a.zip", path = "Download/", mime = "application/zip", mediaType = 0),
    )

    @Test
    fun `only PHOTOS_VIDEOS writes and reconciles photos and videos only`() = runTest {
        val h = IndexerHarness(mixed(), mapOf(Group.PHOTOS_VIDEOS to GroupState.GRANTED))
        val result = h.indexer.run(Mode.FULL, RecordingProgressSink())
        assertThat(h.writer.items.values.map { it.type }).containsExactly(ItemType.PHOTO, ItemType.VIDEO)
        assertThat(h.writer.reconciles.single().toList()).containsExactly(1L, 2L).inOrder()
        assertThat(result.sources.single().error).isNull()
        h.assertInvariants()
    }

    @Test
    fun `only FILES writes no photos or videos`() = runTest {
        val h = IndexerHarness(mixed(), mapOf(Group.FILES to GroupState.GRANTED))
        h.indexer.run(Mode.FULL, RecordingProgressSink())
        assertThat(h.writer.items.values.map { it.type }).containsExactly(ItemType.DOCUMENT, ItemType.OTHER_FILE)
        assertThat(h.writer.reconciles.single().toList()).containsExactly(3L, 4L).inOrder()
        h.assertInvariants()
    }

    @Test
    fun `no usable group returns an empty result and does not query`() = runTest {
        val h = IndexerHarness(mixed(), mapOf(Group.PHOTOS_VIDEOS to GroupState.DENIED))
        val result = h.indexer.run(Mode.FULL, RecordingProgressSink())
        assertThat(result.sources).isEmpty()
        assertThat(h.reader.queries).isEmpty()
        assertThat(h.writer.calls).isEmpty()
    }

    @Test
    fun `a leftover DOCUMENT row is deleted by reconcile once FILES is revoked`() = runTest {
        val h = IndexerHarness(mixed())
        h.indexer.run(Mode.FULL, RecordingProgressSink())
        assertThat(h.item(3)).isNotNull()
        h.permissions.set(Group.FILES, GroupState.DENIED)
        h.reader.rows.removeAll { it.id == 3L || it.id == 4L }
        h.writer.states[Source.MEDIA_STORE] = h.writer.states.getValue(Source.MEDIA_STORE).copy(storeVersion = null)
        val result = h.indexer.run(Mode.FULL, RecordingProgressSink())
        assertThat(result.sources.single().deleted).isEqualTo(2)
        assertThat(h.item(3)).isNull()
        assertThat(h.writer.purges).isEmpty()
    }

    @Test
    fun `rows of a type that is not allowed are neither upserted nor reconciled`() = runTest {
        val h = IndexerHarness(mixed(), mapOf(Group.PHOTOS_VIDEOS to GroupState.GRANTED))
        h.indexer.run(Mode.FULL, RecordingProgressSink())
        assertThat(h.writer.reconciles.single().toList()).doesNotContain(3L)
    }

    @Test
    fun `PARTIAL access adds the media type clause to the INCREMENTAL selection`() = runTest {
        val h = IndexerHarness(emptyList(), mapOf(Group.PHOTOS_VIDEOS to GroupState.PARTIAL))
        h.writer.states[Source.MEDIA_STORE] = SyncState(Source.MEDIA_STORE, 50, "external_primary=v1", 1L, null, null)
        h.indexer.run(Mode.INCREMENTAL, RecordingProgressSink())
        assertThat(h.reader.queries[0].selection).isEqualTo("(generation_modified > ? OR media_type IN (1,3))")
    }

    @Test
    fun `a newly selected photo with an old generation is upserted and a deselected one is deleted`() = runTest {
        val h = IndexerHarness(
            listOf(row(id = 1, gen = 10), row(id = 2, gen = 11)),
            mapOf(Group.PHOTOS_VIDEOS to GroupState.PARTIAL),
        )
        h.indexer.run(Mode.FULL, RecordingProgressSink())
        // user deselects photo 2 and selects photo 3 whose generation is old
        h.reader.rows.removeAll { it.id == 2L }
        h.reader.rows += row(id = 3, gen = 5)
        val result = h.indexer.run(Mode.INCREMENTAL, RecordingProgressSink())
        assertThat(h.item(3)).isNotNull()
        assertThat(h.item(2)).isNull()
        assertThat(result.sources.single().deleted).isEqualTo(1)
        h.assertInvariants()
    }
}
