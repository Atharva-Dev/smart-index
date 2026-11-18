package com.kuriousdragon.smartindex.source.media

import android.database.sqlite.SQLiteException
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SyncState
import com.kuriousdragon.smartindex.source.media.testing.IndexerHarness
import com.kuriousdragon.smartindex.source.media.testing.RecordingProgressSink
import com.kuriousdragon.smartindex.source.media.testing.row
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaStoreIndexerRobustnessTest {
    private fun incState(cursor: Long = 50L) =
        SyncState(Source.MEDIA_STORE, cursor, "external_primary=v1", 42L, 7L, null)

    @Test
    fun `odd rows are indexed and not skipped`() = runTest {
        val h = IndexerHarness(
            listOf(
                row(id = 1, name = null),
                row(id = 2, size = -1L),
                row(id = 3, size = 0L),
                row(id = 4, taken = 4_000_000_000_000L, addedSec = 4_000_000_000L, modifiedSec = 0L),
                row(id = 5, name = "photo.pdf"),
                row(id = 6, size = 6_000_000_000L),
            ),
        )
        val result = h.indexer.run(Mode.FULL, RecordingProgressSink())
        val r = result.sources.single()
        assertThat(r.written).isEqualTo(6)
        assertThat(r.skipped).isEqualTo(0)
        assertThat(h.item(1)!!.title).isEqualTo("Unnamed file")
        assertThat(h.item(2)!!.sizeBytes).isNull()
        assertThat(h.item(3)!!.sizeBytes).isEqualTo(0L)
        assertThat(h.item(4)!!.primaryTs).isEqualTo(h.clock.now)
        assertThat(h.item(6)!!.sizeBytes).isEqualTo(6_000_000_000L)
        h.assertInvariants()
    }

    @Test
    fun `a row without id is skipped as no_id and left out of the reconcile set`() = runTest {
        val h = IndexerHarness(listOf(row(id = null), row(id = 0), row(id = 2)))
        val result = h.indexer.run(Mode.FULL, RecordingProgressSink())
        assertThat(result.sources.single().skipped).isEqualTo(2)
        assertThat(result.sources.single().written).isEqualTo(1)
        assertThat(h.writer.reconciles.single().toList()).containsExactly(2L)
    }

    @Test
    fun `a row whose columns throw is a bad_row and its id is still reconciled as present`() = runTest {
        val h = IndexerHarness(listOf(row(id = 1), row(id = 2), row(id = 3)))
        h.indexer.run(Mode.FULL, RecordingProgressSink())
        h.indexer.rowDecoder = { reader, c ->
            if (reader.id(c) == 2L) error("boom") else reader.row(c)
        }
        h.reader.rows[1] = row(id = 2, name = "changed.jpg", gen = 99)
        val result = h.indexer.run(Mode.FULL, RecordingProgressSink())
        assertThat(result.sources.single().skipped).isEqualTo(1)
        assertThat(result.sources.single().deleted).isEqualTo(0)
        assertThat(h.item(2)!!.title).isEqualTo("IMG_0001.jpg")
        assertThat(h.writer.reconciles.last().toList()).containsExactly(1L, 2L, 3L)
    }

    @Test
    fun `a mapper that throws is a map_error and its id stays present`() = runTest {
        val h = IndexerHarness(listOf(row(id = 1), row(id = 2)))
        h.indexer.mapper = { r, t, now -> if (r.id == 2L) error("boom") else MediaMapperAccess.map(r, t, now) }
        val result = h.indexer.run(Mode.FULL, RecordingProgressSink())
        assertThat(result.sources.single().skipped).isEqualTo(1)
        assertThat(result.sources.single().written).isEqualTo(1)
        assertThat(h.writer.reconciles.single().toList()).containsExactly(1L, 2L)
    }

    @Test
    fun `SecurityException on the rows query fails with permission_revoked and no reconcile`() = runTest {
        val h = IndexerHarness(listOf(row(id = 1, gen = 60)))
        h.writer.states[Source.MEDIA_STORE] = incState()
        h.reader.failOnQuery(1, SecurityException("no"))
        val result = h.indexer.run(Mode.INCREMENTAL, RecordingProgressSink())
        assertThat(result.sources.single().error).isEqualTo("media_store:permission_revoked:SecurityException")
        assertThat(h.writer.reconciles).isEmpty()
        assertThat(h.writer.states.getValue(Source.MEDIA_STORE).cursor).isEqualTo(50L)
    }

    @Test
    fun `SecurityException on the id pass keeps the last checkpoint and does not reconcile`() = runTest {
        val h = IndexerHarness(listOf(row(id = 1, gen = 60)))
        h.writer.states[Source.MEDIA_STORE] = incState()
        h.reader.failOnQuery(2, SecurityException("no"))
        val result = h.indexer.run(Mode.INCREMENTAL, RecordingProgressSink())
        assertThat(result.sources.single().error).isEqualTo("media_store:permission_revoked:SecurityException")
        assertThat(h.writer.reconciles).isEmpty()
        assertThat(h.writer.states.getValue(Source.MEDIA_STORE).cursor).isEqualTo(59L)
        assertThat(h.reader.closedCursors).isEqualTo(h.reader.openedCursors)
    }

    @Test
    fun `FULL failure saves no state`() = runTest {
        val h = IndexerHarness(listOf(row(id = 1)))
        h.reader.failOnQuery(1, SecurityException("no"))
        val result = h.indexer.run(Mode.FULL, RecordingProgressSink())
        assertThat(result.sources.single().mode).isEqualTo(Mode.FULL)
        assertThat(h.writer.savedStates).isEmpty()
        assertThat(h.writer.reconciles).isEmpty()
    }

    @Test
    fun `a null cursor fails with provider_unavailable`() = runTest {
        val h = IndexerHarness(listOf(row(id = 1)))
        h.reader.nullCursorOnQuery(1)
        val result = h.indexer.run(Mode.FULL, RecordingProgressSink())
        assertThat(result.sources.single().error).isEqualTo("media_store:provider_unavailable:none")
        assertThat(h.writer.reconciles).isEmpty()
    }

    @Test
    fun `a provider exception fails with provider_error and the class name`() = runTest {
        val h = IndexerHarness(listOf(row(id = 1)))
        h.reader.failOnQuery(1, SQLiteException("x"))
        val result = h.indexer.run(Mode.FULL, RecordingProgressSink())
        assertThat(result.sources.single().error).isEqualTo("media_store:provider_error:SQLiteException")
    }

    @Test
    fun `a writer that throws on batch two fails with write_failed and no reconcile`() = runTest {
        val h = IndexerHarness((1L..1200L).map { row(id = it, gen = it) })
        h.writer.states[Source.MEDIA_STORE] = incState(0L)
        h.writer.failOnUpsert(2, IllegalStateException("disk"))
        val result = h.indexer.run(Mode.INCREMENTAL, RecordingProgressSink())
        assertThat(result.sources.single().error).isEqualTo("media_store:write_failed:IllegalStateException")
        assertThat(h.writer.reconciles).isEmpty()
        // the checkpoint of batch one is the last durable state
        assertThat(h.writer.states.getValue(Source.MEDIA_STORE).cursor).isEqualTo(499L)
    }

    @Test
    fun `a full disk is reported as STORAGE_FULL`() = runTest {
        val h = IndexerHarness(listOf(row(id = 1)))
        h.writer.failOnUpsert(1, RuntimeException("SQLITE_FULL"))
        val result = h.indexer.run(Mode.FULL, RecordingProgressSink())
        assertThat(result.sources.single().error).isEqualTo("media_store:write_failed:STORAGE_FULL")
    }

    @Test
    fun `cancelling after the first batch rethrows, cancels the signal and does not reconcile`() = runTest {
        val h = IndexerHarness((1L..1200L).map { row(id = it, gen = it) }, io = UnconfinedTestDispatcher(testScheduler))
        h.writer.states[Source.MEDIA_STORE] = incState(0L)
        lateinit var job: kotlinx.coroutines.Deferred<*>
        val sink = RecordingProgressSink { done, _ -> if (done == 500) job.cancel() }
        job = async(start = CoroutineStart.LAZY) { h.indexer.run(Mode.INCREMENTAL, sink) }
        job.start()
        var thrown: Throwable? = null
        try {
            job.await()
        } catch (e: CancellationException) {
            thrown = e
        }
        assertThat(thrown).isInstanceOf(CancellationException::class.java)
        assertThat(h.writer.reconciles).isEmpty()
        assertThat(h.writer.upsertSizes).containsExactly(500)
        assertThat(h.reader.queries[0].signal!!.isCanceled).isTrue()
        assertThat(h.reader.closedCursors).isEqualTo(h.reader.openedCursors)
        assertThat(h.writer.states.getValue(Source.MEDIA_STORE).cursor).isEqualTo(499L)
    }
}
