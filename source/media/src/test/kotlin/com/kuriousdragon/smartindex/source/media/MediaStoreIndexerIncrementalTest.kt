package com.kuriousdragon.smartindex.source.media

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
class MediaStoreIndexerIncrementalTest {
    private fun stateOf(cursor: Long) =
        SyncState(Source.MEDIA_STORE, cursor, "external_primary=v1", 42L, 7L, "old")

    @Test
    fun `INCREMENTAL reads only rows above the cursor and keeps lastFullReconcile`() = runTest {
        val rows = (1L..1300L).map { row(id = it, gen = it) }
        val h = IndexerHarness(rows)
        h.writer.states[Source.MEDIA_STORE] = stateOf(100L)
        val result = h.indexer.run(Mode.INCREMENTAL, RecordingProgressSink())
        val rowsQuery = h.reader.queries[0]
        assertThat(rowsQuery.selection).isEqualTo("generation_modified > ?")
        assertThat(rowsQuery.args).containsExactly("100")
        assertThat(rowsQuery.sort).isEqualTo("generation_modified ASC, _id ASC")
        assertThat(h.writer.upsertSizes).containsExactly(500, 500, 200).inOrder()
        assertThat(result.sources.single().mode).isEqualTo(Mode.INCREMENTAL)
        // checkpoints after each batch = last generation in batch - 1, then the final cursor
        assertThat(h.writer.savedStates.map { it.cursor }).containsExactly(599L, 1099L, 1299L, 1300L).inOrder()
        val last = h.writer.states.getValue(Source.MEDIA_STORE)
        assertThat(last.lastFullReconcile).isEqualTo(42L)
        assertThat(last.lastRun).isEqualTo(7L)
        assertThat(last.lastError).isEqualTo("old")
        assertThat(h.writer.reconciles.single().toList()).isEqualTo((1L..1300L).toList())
        // second query is the ID pass over all rows
        val idQuery = h.reader.queries[1]
        assertThat(idQuery.selection).isNull()
        assertThat(idQuery.sort).isEqualTo("_id ASC")
        assertThat(idQuery.projection).contains("_id")
        assertThat(idQuery.projection).doesNotContain("size")
        h.assertInvariants()
    }

    @Test
    fun `checkpoint never drops below the previous cursor`() = runTest {
        val h = IndexerHarness(listOf(row(id = 1, gen = 51), row(id = 2, gen = 51)))
        h.writer.states[Source.MEDIA_STORE] = stateOf(50L)
        h.indexer.run(Mode.INCREMENTAL, RecordingProgressSink())
        assertThat(h.writer.savedStates.map { it.cursor }).containsExactly(50L, 51L).inOrder()
    }

    @Test
    fun `a moved file keeps its id and is upserted once with the new folder and title`() = runTest {
        val h = IndexerHarness(listOf(row(id = 7, name = "old.jpg", path = "DCIM/Camera/", gen = 10)))
        h.indexer.run(Mode.FULL, RecordingProgressSink())
        h.reader.rows[0] = row(id = 7, name = "new.jpg", path = "Pictures/Trip/", gen = 20)
        val before = h.writer.upserts.size
        val result = h.indexer.run(Mode.INCREMENTAL, RecordingProgressSink())
        assertThat(h.writer.upserts.size).isEqualTo(before + 1)
        assertThat(h.writer.upserts.last().single().title).isEqualTo("new.jpg")
        assertThat(h.writer.upserts.last().single().folder).isEqualTo("Trip")
        assertThat(result.sources.single().deleted).isEqualTo(0)
        assertThat(h.item(7)!!.title).isEqualTo("new.jpg")
    }

    @Test
    fun `a deleted file is gone from the id pass and counted as deleted`() = runTest {
        val h = IndexerHarness(listOf(row(id = 1, gen = 1), row(id = 2, gen = 2)))
        h.indexer.run(Mode.FULL, RecordingProgressSink())
        h.reader.rows.removeAt(1)
        val upsertsBefore = h.writer.upserts.size
        val result = h.indexer.run(Mode.INCREMENTAL, RecordingProgressSink())
        assertThat(result.sources.single().deleted).isEqualTo(1)
        assertThat(h.item(2)).isNull()
        assertThat(h.item(1)).isNotNull()
        assertThat(h.writer.upserts).hasSize(upsertsBefore)
    }

    @Test
    fun `INCREMENTAL progress starts at zero and ends with total`() = runTest {
        val h = IndexerHarness(listOf(row(id = 1, gen = 60), row(id = 2, gen = 61)))
        h.writer.states[Source.MEDIA_STORE] = stateOf(50L)
        val sink = RecordingProgressSink()
        h.indexer.run(Mode.INCREMENTAL, sink)
        assertThat(sink.reports.first()).isEqualTo(0 to 2)
        assertThat(sink.reports.last()).isEqualTo(2 to 2)
    }
}
