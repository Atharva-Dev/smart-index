package com.kuriousdragon.smartindex.core.index.write

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.index.IndexDbTest
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SyncState
import kotlinx.coroutines.runBlocking
import org.junit.Test

class UpsertTest : IndexDbTest() {
    private fun upsert(
        vararg r: com.kuriousdragon.smartindex.core.model.ItemRecord,
    ) = runBlocking { writer.upsert(r.toList()) }

    private fun upsert(r: List<com.kuriousdragon.smartindex.core.model.ItemRecord>) = runBlocking { writer.upsert(r) }

    private fun contactName(norm: String, name: String) =
        read { c ->
            c.usePrepared("INSERT INTO contact_name(number_norm, name) VALUES ('$norm', '$name')") { it.step() }
        }

    @Test
    fun `re-upserting the same batch is idempotent`() {
        val batch = listOf(photo(1), doc(2), sms(3), call(4), recording(5))
        upsert(batch)
        val ids = longs("SELECT id FROM item ORDER BY id")
        upsert(batch)
        assertThat(longs("SELECT id FROM item ORDER BY id")).isEqualTo(ids)
        for (t in listOf("item", "item_key", "short_text", "fts_short")) assertThat(count(t)).isEqualTo(5)
        assertThat(count("item_date")).isEqualTo(6) // photo 1, doc 2, sms 1, call 1, recording 1
        assertThat(match("img_1.jpg")).hasSize(1)
    }

    @Test
    fun `changed version re-indexes the search text`() {
        upsert(photo(1, title = "oldname.jpg"))
        val id = idOf(Source.MEDIA_STORE, 1)
        upsert(photo(1, title = "newname.jpg", version = 2))
        assertThat(match("oldname")).isEmpty()
        assertThat(match("newname")).containsExactly(id)
        assertThat(idOf(Source.MEDIA_STORE, 1)).isEqualTo(id)
    }

    @Test
    fun `equal version is skipped and a smaller new version still updates`() {
        upsert(photo(1, title = "first.jpg", version = 5))
        upsert(photo(1, title = "ignored.jpg", version = 5))
        assertThat(match("ignored")).isEmpty()
        assertThat(match("first")).hasSize(1)
        upsert(photo(1, title = "smaller.jpg", version = 2))
        assertThat(match("smaller")).hasSize(1)
        assertThat(longs("SELECT source_version FROM item")).containsExactly(2L)
    }

    @Test
    fun `last record wins for a duplicate key`() {
        upsert(photo(1, title = "one.jpg", version = 1), photo(1, title = "two.jpg", version = 2))
        assertThat(count("item")).isEqualTo(1)
        assertThat(texts("SELECT title FROM item")).containsExactly("two.jpg")
    }

    @Test
    fun `1201 records are all stored across chunks`() {
        upsert((1L..1201L).map { photo(it) })
        assertThat(count("item")).isEqualTo(1201)
        assertThat(count("item_key")).isEqualTo(1201)
        assertThat(count("fts_short")).isEqualTo(1201)
    }

    @Test
    fun `body is dropped for a document and kept for a message`() {
        upsert(doc(1), sms(2, body = "Hotel Sea View"))
        assertThat(texts("SELECT body FROM item WHERE type = 3")).containsExactly(null)
        assertThat(texts("SELECT body FROM item WHERE type = 7")).containsExactly("Hotel Sea View")
        assertThat(match("secret body")).isEmpty()
        assertThat(match("sea view")).hasSize(1)
    }

    @Test
    fun `dates are derived per type`() {
        upsert(photo(1), photo(2, taken = null, added = 9), doc(3), sms(4), call(5), recording(6))
        fun kinds(source: Source, sid: Long) =
            longs("SELECT kind FROM item_date WHERE item_id = ${idOf(source, sid)} ORDER BY kind")
        assertThat(kinds(Source.MEDIA_STORE, 1)).containsExactly(1L)
        assertThat(kinds(Source.MEDIA_STORE, 2)).containsExactly(2L)
        assertThat(kinds(Source.MEDIA_STORE, 3)).containsExactly(2L, 3L).inOrder()
        assertThat(kinds(Source.SMS, 4)).containsExactly(4L)
        assertThat(kinds(Source.CALL_LOG, 5)).containsExactly(4L)
        assertThat(kinds(Source.MEDIA_STORE, 6)).containsExactly(4L)
    }

    @Test
    fun `changed version replaces the date rows`() {
        upsert(doc(1))
        upsert(doc(1, version = 2).copy(dates = mapOf(com.kuriousdragon.smartindex.core.model.DateKind.MODIFIED to 9L)))
        assertThat(longs("SELECT kind FROM item_date")).containsExactly(3L)
        assertThat(longs("SELECT ts FROM item_date")).containsExactly(9L)
    }

    @Test
    fun `contact name is copied from the cache for telephony only`() {
        contactName("+919876543210", "Rahul")
        upsert(sms(1), call(2), photo(3, title = "x.jpg"))
        assertThat(
            texts("SELECT contact_name FROM item WHERE source IN (2, 3) ORDER BY source"),
        ).containsExactly("Rahul", "Rahul")
        assertThat(texts("SELECT contact_name FROM item WHERE source = 1")).containsExactly(null)
        assertThat(match("rahul")).hasSize(2)
    }

    @Test
    fun `number_norm is normalised and null without a number`() {
        upsert(sms(1, number = "098765 43210"), sms(2, number = null), sms(3, number = "AX-HDFCBK"))
        assertThat(texts("SELECT number_norm FROM item ORDER BY source_id"))
            .containsExactly("+919876543210", null, "AX-HDFCBK").inOrder()
    }

    @Test
    fun `raw and digits-only numbers are both searchable`() {
        upsert(sms(1, number = "+91 98765 43210"))
        assertThat(match("9876543210")).hasSize(1)
        assertThat(match("98765 43210")).hasSize(1)
        assertThat(match("919876543210")).hasSize(1)
    }

    @Test
    fun `alphanumeric sender has no digits form`() {
        upsert(sms(1, number = "AX-HDFCBK", body = "hello"))
        assertThat(texts("SELECT skey FROM short_text").single()).isEqualTo("ax-hdfcbk\u0001ax-hdfcbk\u0001hello")
    }

    @Test
    fun `normaliser is called once per distinct number per chunk`() {
        upsert((1L..10L).map { sms(it, number = if (it % 2L == 0L) "+91 98765 43210" else "098765 43211") })
        assertThat(numbers.normalizeCalls["+91 98765 43210"]).isEqualTo(1)
        assertThat(numbers.normalizeCalls["098765 43211"]).isEqualTo(1)
    }

    @Test
    fun `sync state round-trips including nulls`() = runBlocking {
        assertThat(writer.syncState(Source.SMS)).isNull()
        val full = SyncState(Source.SMS, 42, "v1", 100, 200, "oops")
        writer.saveSyncState(full)
        assertThat(writer.syncState(Source.SMS)).isEqualTo(full)
        val empty = SyncState(Source.SMS, 0, null, null, null, null)
        writer.saveSyncState(empty)
        assertThat(writer.syncState(Source.SMS)).isEqualTo(empty)
        assertThat(writer.syncState(Source.MMS)).isNull()
    }
}
