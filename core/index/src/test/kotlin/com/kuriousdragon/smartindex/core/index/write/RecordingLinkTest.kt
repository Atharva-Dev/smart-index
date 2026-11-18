package com.kuriousdragon.smartindex.core.index.write

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.index.IndexDbTest
import com.kuriousdragon.smartindex.core.model.RecordingLink
import com.kuriousdragon.smartindex.core.model.ResultItem
import com.kuriousdragon.smartindex.core.model.SearchQuery
import com.kuriousdragon.smartindex.core.model.Source
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

class RecordingLinkTest : IndexDbTest() {
    private var c1 = 0L
    private var c2 = 0L
    private var r1 = 0L
    private var r2 = 0L

    private fun seed() {
        runBlocking { writer.upsert(listOf(call(1), call(2), recording(10), recording(11), sms(1), photo(5))) }
        c1 = idOf(Source.CALL_LOG, 1)
        c2 = idOf(Source.CALL_LOG, 2)
        r1 = idOf(Source.MEDIA_STORE, 10)
        r2 = idOf(Source.MEDIA_STORE, 11)
    }

    private fun link(c: Long, r: Long) = RecordingLink(c, r, 0.5f, "time")

    private fun pairs() =
        read { con ->
            con.usePrepared("SELECT call_item_id, recording_item_id FROM recording_link ORDER BY call_item_id") { st ->
                buildList { while (st.step()) add(st.getLong(0) to st.getLong(1)) }
            }
        }

    @Test
    fun `replace adds links`() {
        seed()
        runBlocking { writer.replaceRecordingLinks(listOf(link(c1, r1), link(c2, r2))) }
        assertThat(pairs()).containsExactly(c1 to r1, c2 to r2)
        assertThat(texts("SELECT method FROM recording_link LIMIT 1")).containsExactly("time")
    }

    @Test
    fun `incremental replace drops old links of the same call or recording`() {
        seed()
        runBlocking { writer.replaceRecordingLinks(listOf(link(c1, r1))) }
        runBlocking { writer.replaceRecordingLinks(listOf(link(c1, r2))) }
        assertThat(pairs()).containsExactly(c1 to r2)
        runBlocking { writer.replaceRecordingLinks(listOf(link(c2, r2))) }
        assertThat(pairs()).containsExactly(c2 to r2)
    }

    @Test
    fun `incremental replace keeps unrelated links`() {
        seed()
        runBlocking { writer.replaceRecordingLinks(listOf(link(c1, r1))) }
        runBlocking { writer.replaceRecordingLinks(listOf(link(c2, r2))) }
        assertThat(pairs()).containsExactly(c1 to r1, c2 to r2)
    }

    @Test
    fun `duplicate ids throw and write nothing`() {
        seed()
        runBlocking { writer.replaceRecordingLinks(listOf(link(c1, r1))) }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { writer.replaceRecordingLinks(listOf(link(c2, r1), link(c2, r2))) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { writer.replaceAllRecordingLinks(listOf(link(c1, r2), link(c2, r2))) }
        }
        assertThat(pairs()).containsExactly(c1 to r1)
    }

    @Test
    fun `unknown or wrong-type ids are skipped`() {
        seed()
        val sms = idOf(Source.SMS, 1)
        val photo = idOf(Source.MEDIA_STORE, 5)
        runBlocking {
            writer.replaceRecordingLinks(
                listOf(link(sms, r2), link(c2, 8_888), link(9_999, photo), link(c1, r1)),
            )
        }
        assertThat(pairs()).containsExactly(c1 to r1)
    }

    @Test
    fun `replaceAll clears the other links`() {
        seed()
        runBlocking { writer.replaceRecordingLinks(listOf(link(c1, r1))) }
        runBlocking { writer.replaceAllRecordingLinks(listOf(link(c2, r2))) }
        assertThat(pairs()).containsExactly(c2 to r2)
        runBlocking { writer.replaceAllRecordingLinks(emptyList()) }
        assertThat(count("recording_link")).isEqualTo(0)
    }

    @Test
    fun `deleting a linked item removes the link`() {
        seed()
        runBlocking { writer.replaceRecordingLinks(listOf(link(c1, r1))) }
        read { it.usePrepared("DELETE FROM item WHERE id = $r1") { st -> st.step() } }
        assertThat(count("recording_link")).isEqualTo(0)
    }

    private fun all(): List<ResultItem> = query.search(
        SearchQuery("", com.kuriousdragon.smartindex.core.model.DateRange(0, 100_000), null),
    ).all()

    @Test
    fun `result items expose the linked recording on calls and the call's name and number on recordings`() {
        seed()
        runBlocking {
            writer.setContactNames(mapOf("+919876543210" to "Rahul"))
            writer.replaceRecordingLinks(listOf(link(c1, r1)))
        }
        val items = all().associateBy { it.id }
        assertThat(items.getValue(c1).linkedRecordingId).isEqualTo(r1)
        assertThat(items.getValue(c2).linkedRecordingId).isNull()
        val rec = items.getValue(r1)
        assertThat(rec.contactName).isEqualTo("Rahul")
        assertThat(rec.number).isEqualTo("+91 98765 43210")
        assertThat(rec.linkedRecordingId).isNull()
        val unlinked = items.getValue(r2)
        assertThat(unlinked.contactName).isNull()
        assertThat(unlinked.number).isNull()
        assertThat(items.getValue(idOf(Source.SMS, 1)).linkedRecordingId).isNull()
    }
}
