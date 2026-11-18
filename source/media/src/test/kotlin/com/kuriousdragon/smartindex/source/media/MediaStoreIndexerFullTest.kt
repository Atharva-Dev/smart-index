package com.kuriousdragon.smartindex.source.media

import android.provider.MediaStore.Files.FileColumns
import com.google.common.truth.Truth.assertThat
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
class MediaStoreIndexerFullTest {
    private fun bigRows() = buildList {
        for (i in 1L..1201L) add(row(id = i, name = "IMG_$i.jpg", gen = 1000L + i))
        for (i in 2001L..2030L) add(row(id = i, name = "dir$i", mime = null, mediaType = 0, gen = 5L))
        for (i in 3001L..3010L) add(row(id = i, name = ".hidden$i.jpg", gen = 5L))
    }

    @Test
    fun `FULL with 1201 eligible rows upserts in batches of 500 and reconciles the exact ids`() = runTest {
        val h = IndexerHarness(bigRows())
        h.writer.states[Source.MEDIA_STORE] = SyncState(Source.MEDIA_STORE, 0, "old", 5L, 111L, "boom")
        h.clock.now = 1_800_000_000_000L
        val result = h.indexer.run(Mode.FULL, RecordingProgressSink())
        assertThat(h.writer.upsertSizes).containsExactly(500, 500, 201).inOrder()
        assertThat(h.writer.reconciles).hasSize(1)
        assertThat(h.writer.reconciles.single().toList()).isEqualTo((1L..1201L).toList())
        val r = result.sources.single()
        assertThat(r.mode).isEqualTo(Mode.FULL)
        assertThat(r.written).isEqualTo(1201)
        assertThat(r.error).isNull()
        val s = h.writer.states.getValue(Source.MEDIA_STORE)
        assertThat(s.cursor).isEqualTo(2201L)
        assertThat(s.storeVersion).isEqualTo("external_primary=v1")
        assertThat(s.lastFullReconcile).isEqualTo(1_800_000_000_000L)
        assertThat(s.lastRun).isEqualTo(111L)
        assertThat(s.lastError).isEqualTo("boom")
        assertThat(h.writer.calls.last()).isEqualTo("saveSyncState")
        assertThat(h.writer.calls.indexOf("reconcile")).isGreaterThan(h.writer.calls.lastIndexOf("upsert"))
        h.assertInvariants()
    }

    @Test
    fun `FULL counts excluded rows and sorts by id`() = runTest {
        val h = IndexerHarness(bigRows())
        h.indexer.run(Mode.FULL, RecordingProgressSink())
        val q = h.reader.queries.single()
        assertThat(q.sort).isEqualTo("_id ASC")
        assertThat(q.selection).isNull()
        assertThat(q.projection).containsExactlyElementsIn(
            com.kuriousdragon.smartindex.source.media.store.MediaQueries.ROW_PROJECTION.toList(),
        )
        assertThat(h.reader.closedCursors).isEqualTo(1)
    }

    @Test
    fun `INCREMENTAL without state runs FULL and reports mode FULL`() = runTest {
        val h = IndexerHarness(listOf(row(id = 1), row(id = 2, gen = 11)))
        val result = h.indexer.run(Mode.INCREMENTAL, RecordingProgressSink())
        assertThat(result.sources.single().mode).isEqualTo(Mode.FULL)
        assertThat(h.reader.queries.single().selection).isNull()
        assertThat(h.writer.states.getValue(Source.MEDIA_STORE).cursor).isEqualTo(11L)
    }

    @Test
    fun `FULL requested with state ignores the cursor`() = runTest {
        val h = IndexerHarness(listOf(row(id = 1, gen = 3), row(id = 2, gen = 4)))
        h.writer.states[Source.MEDIA_STORE] = SyncState(Source.MEDIA_STORE, 100, "external_primary=v1", 1L, null, null)
        val result = h.indexer.run(Mode.FULL, RecordingProgressSink())
        assertThat(result.sources.single().written).isEqualTo(2)
        assertThat(h.reader.queries.single().selection).isNull()
        assertThat(h.writer.states.getValue(Source.MEDIA_STORE).cursor).isEqualTo(4L)
    }

    @Test
    fun `version change by an added volume escalates to FULL and reads the new volume`() = runTest {
        val h = IndexerHarness(listOf(row(id = 1)))
        h.indexer.run(Mode.FULL, RecordingProgressSink())
        h.reader.versions["1a2b-3c4d"] = "B"
        h.reader.rows += row(id = 2, volume = "1a2b-3c4d", gen = 20)
        val result = h.indexer.run(Mode.INCREMENTAL, RecordingProgressSink())
        assertThat(result.sources.single().mode).isEqualTo(Mode.FULL)
        assertThat(h.item(2)).isNotNull()
        assertThat(h.writer.states.getValue(Source.MEDIA_STORE).storeVersion)
            .isEqualTo("1a2b-3c4d=B;external_primary=v1")
    }

    @Test
    fun `version change by a removed volume escalates to FULL and its rows disappear through reconcile`() = runTest {
        val h = IndexerHarness(listOf(row(id = 1), row(id = 2, volume = "sd")))
        h.reader.versions["sd"] = "B"
        h.indexer.run(Mode.FULL, RecordingProgressSink())
        assertThat(h.item(2)).isNotNull()
        h.reader.versions.remove("sd")
        h.reader.rows.removeAll { it.volumeName == "sd" }
        val result = h.indexer.run(Mode.INCREMENTAL, RecordingProgressSink())
        assertThat(result.sources.single().mode).isEqualTo(Mode.FULL)
        assertThat(result.sources.single().deleted).isEqualTo(1)
        assertThat(h.item(2)).isNull()
    }

    @Test
    fun `version change by a changed version string escalates to FULL`() = runTest {
        val h = IndexerHarness(listOf(row(id = 1)))
        h.indexer.run(Mode.FULL, RecordingProgressSink())
        h.reader.versions["external_primary"] = "v2"
        val result = h.indexer.run(Mode.INCREMENTAL, RecordingProgressSink())
        assertThat(result.sources.single().mode).isEqualTo(Mode.FULL)
        assertThat(h.writer.states.getValue(Source.MEDIA_STORE).storeVersion).isEqualTo("external_primary=v2")
    }

    @Test
    fun `progress starts at zero, reports once per batch and ends with total`() = runTest {
        val h = IndexerHarness(bigRows())
        val sink = RecordingProgressSink()
        h.indexer.run(Mode.FULL, sink)
        val total = 1241
        assertThat(sink.reports.first()).isEqualTo(0 to total)
        assertThat(sink.reports.last()).isEqualTo(total to total)
        // 3 batch reports between the first and the last
        assertThat(sink.reports).hasSize(5)
        assertThat(sink.reports.map { it.second }.toSet()).containsExactly(total)
    }

    @Test
    fun `documents recordings and other files are written when FILES is granted`() = runTest {
        val h = IndexerHarness(
            listOf(
                row(id = 1),
                row(id = 2, name = "a.pdf", path = "Download/", mime = "application/pdf", mediaType = 0),
                row(
                    id = 3,
                    name = "x.m4a",
                    path = "Recordings/Call/",
                    mime = "audio/mp4",
                    mediaType = FileColumns.MEDIA_TYPE_AUDIO,
                ),
                row(id = 4, name = "a.zip", path = "Download/", mime = "application/zip", mediaType = 0),
            ),
        )
        val result = h.indexer.run(Mode.FULL, RecordingProgressSink())
        assertThat(result.sources.single().written).isEqualTo(4)
        assertThat(h.writer.items.values.map { it.type.name })
            .containsExactly("PHOTO", "DOCUMENT", "RECORDING", "OTHER_FILE")
        h.assertInvariants()
    }
}
