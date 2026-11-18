package com.kuriousdragon.smartindex.core.index.query

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.index.IndexDbTest
import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.MsgKind
import com.kuriousdragon.smartindex.core.model.RecordingLink
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceKey
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** `IndexQuery.item`, `stats` and `recordingCandidates` (LLD §7.3). */
class DetailStatsCandidatesTest : IndexDbTest() {
    private val dayMs = 86_400_000L

    private fun link(c: Long, r: Long) = RecordingLink(c, r, 0.5f, "time")

    @Test
    fun `item returns the full detail with the dates map`() {
        runBlocking { writer.upsert(listOf(doc(1), sms(2))) }
        val d = runBlocking { query.item(idOf(Source.MEDIA_STORE, 1)) }!!
        assertThat(d.key).isEqualTo(SourceKey(Source.MEDIA_STORE, 1))
        assertThat(d.type).isEqualTo(ItemType.DOCUMENT)
        assertThat(d.title).isEqualTo("report_1.pdf")
        assertThat(d.dates).containsExactly(DateKind.ADDED, 5_000L, DateKind.MODIFIED, 6_000L)
        assertThat(d.folder).isEqualTo("Download")
        assertThat(d.ext).isEqualTo("pdf")
        assertThat(d.body).isNull() // document text is never stored
        assertThat(d.linkedRecordingId).isNull()
        assertThat(d.linkedCallId).isNull()

        val m = runBlocking { query.item(idOf(Source.SMS, 2)) }!!
        assertThat(m.type).isEqualTo(ItemType.MESSAGE)
        assertThat(m.direction).isEqualTo(Direction.RECEIVED)
        assertThat(m.msgKind).isEqualTo(MsgKind.SMS)
        assertThat(m.body).isEqualTo("Hotel Sea View")
        assertThat(m.threadId).isEqualTo(7L)
        assertThat(m.dates).containsExactly(DateKind.EVENT, 10_002L)
    }

    @Test
    fun `item carries both link ids`() {
        runBlocking { writer.upsert(listOf(call(1), recording(10))) }
        val c = idOf(Source.CALL_LOG, 1)
        val r = idOf(Source.MEDIA_STORE, 10)
        runBlocking { writer.replaceRecordingLinks(listOf(link(c, r))) }
        assertThat(runBlocking { query.item(c) }!!.linkedRecordingId).isEqualTo(r)
        assertThat(runBlocking { query.item(c) }!!.linkedCallId).isNull()
        assertThat(runBlocking { query.item(r) }!!.linkedCallId).isEqualTo(c)
        assertThat(runBlocking { query.item(r) }!!.linkedRecordingId).isNull()
    }

    @Test
    fun `item of a missing or deleted id is null`() {
        assertThat(runBlocking { query.item(999) }).isNull()
        runBlocking { writer.upsert(listOf(photo(1))) }
        val id = idOf(Source.MEDIA_STORE, 1)
        assertThat(runBlocking { query.item(id) }).isNotNull()
        runBlocking { writer.purge(Source.MEDIA_STORE) }
        assertThat(runBlocking { query.item(id) }).isNull()
    }

    @Test
    fun `stats emits zero-filled counts then updates after upserts and link changes`() = runBlocking {
        query.stats().test {
            val first = awaitItem()
            assertThat(first.countsByType.keys).containsExactlyElementsIn(ItemType.entries)
            assertThat(first.totalItems).isEqualTo(0)
            assertThat(first.recordingsLinked).isEqualTo(0)

            writer.upsert(listOf(photo(1), photo(2), call(1), recording(10)))
            var s = awaitItem()
            while (s.totalItems != 4) s = awaitItem()
            assertThat(s.countsByType[ItemType.PHOTO]).isEqualTo(2)
            assertThat(s.countsByType[ItemType.CALL]).isEqualTo(1)
            assertThat(s.countsByType[ItemType.RECORDING]).isEqualTo(1)
            assertThat(s.countsByType[ItemType.MESSAGE]).isEqualTo(0)

            writer.replaceRecordingLinks(listOf(link(idOf(Source.CALL_LOG, 1), idOf(Source.MEDIA_STORE, 10))))
            while (s.recordingsLinked != 1) s = awaitItem()
            assertThat(s.totalItems).isEqualTo(4)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `candidates for zero return everything`() {
        runBlocking { writer.upsert(listOf(call(1), call(2), recording(10), sms(1), photo(1))) }
        val c1 = idOf(Source.CALL_LOG, 1)
        val r1 = idOf(Source.MEDIA_STORE, 10)
        runBlocking { writer.replaceRecordingLinks(listOf(link(c1, r1))) }
        val inputs = runBlocking { query.recordingCandidates(0) }
        assertThat(inputs.calls.map { it.primaryTs }).containsExactly(20_001L, 20_002L).inOrder()
        assertThat(inputs.calls.first().direction).isEqualTo(Direction.INCOMING)
        assertThat(inputs.calls.first().durationMs).isEqualTo(30_000L)
        assertThat(inputs.calls.first().numberNorm).isNotNull()
        assertThat(inputs.recordings).hasSize(1)
        assertThat(inputs.recordings.single().dates)
            .containsExactly(DateKind.EVENT, 30_000L, DateKind.MODIFIED, 31_000L)
        assertThat(inputs.links).containsExactly(link(c1, r1))
    }

    @Test
    fun `candidates with since use a 24 hour margin and only links touching returned items`() {
        val t = 100 * dayMs
        fun callAt(id: Long, ts: Long) = call(id).copy(primaryTs = ts, dates = mapOf(DateKind.EVENT to ts))
        fun recAt(id: Long, ts: Long) = recording(id).copy(primaryTs = ts, dates = mapOf(DateKind.EVENT to ts))
        runBlocking {
            writer.upsert(
                listOf(
                    callAt(1, t - dayMs + 1), // inside the margin
                    callAt(2, t - dayMs - 1), // just outside
                    callAt(3, t + 5),
                    recAt(10, t + 10),
                    recAt(11, t - 2 * dayMs),
                ),
            )
        }
        val c1 = idOf(Source.CALL_LOG, 1)
        val c2 = idOf(Source.CALL_LOG, 2)
        val c3 = idOf(Source.CALL_LOG, 3)
        val r10 = idOf(Source.MEDIA_STORE, 10)
        val r11 = idOf(Source.MEDIA_STORE, 11)
        runBlocking { writer.replaceRecordingLinks(listOf(link(c1, r10), link(c2, r11))) }
        val inputs = runBlocking { query.recordingCandidates(t) }
        assertThat(inputs.calls.map { it.id }).containsExactly(c1, c3).inOrder()
        assertThat(inputs.recordings.map { it.id }).containsExactly(r10)
        assertThat(inputs.links).containsExactly(link(c1, r10))
    }

    @Test
    fun `candidates fetch links in chunks for many items`() {
        val t = 100 * dayMs
        val calls = (1L..1_200L).map { call(it).copy(primaryTs = t + it, dates = mapOf(DateKind.EVENT to t + it)) }
        runBlocking { writer.upsert(calls) }
        val ids = (1L..1_200L).map { idOf(Source.CALL_LOG, it) }
        val recs = (1L..3L).map { recording(it).copy(primaryTs = t, dates = mapOf(DateKind.EVENT to t)) }
        runBlocking { writer.upsert(recs) }
        val rids = (1L..3L).map { idOf(Source.MEDIA_STORE, it) }
        runBlocking {
            writer.replaceRecordingLinks(
                listOf(link(ids[0], rids[0]), link(ids[700], rids[1]), link(ids[1_199], rids[2])),
            )
        }
        val inputs = runBlocking { query.recordingCandidates(t) }
        assertThat(inputs.calls).hasSize(1_200)
        assertThat(inputs.links).hasSize(3)
    }
}
