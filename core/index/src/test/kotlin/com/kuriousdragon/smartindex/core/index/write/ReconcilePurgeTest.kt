package com.kuriousdragon.smartindex.core.index.write

import androidx.collection.longSetOf
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.index.IndexDbTest
import com.kuriousdragon.smartindex.core.model.ItemRecord
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.RecordingLink
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SyncState
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ReconcilePurgeTest : IndexDbTest() {
    private fun upsert(r: List<ItemRecord>) = runBlocking { writer.upsert(r) }

    private fun items(source: Int) = longs("SELECT COUNT(*) FROM item WHERE source = $source").single()

    @Test
    fun `reconcile deletes the missing ids everywhere and returns the count`() {
        upsert((1L..10L).map { photo(it) })
        val gone = (8L..10L).map { idOf(Source.MEDIA_STORE, it) }
        val n = runBlocking { writer.reconcile(Source.MEDIA_STORE, longSetOf(1, 2, 3, 4, 5, 6, 7)) }
        assertThat(n).isEqualTo(3)
        for (t in listOf("item", "item_key", "short_text", "fts_short")) assertThat(count(t)).isEqualTo(7)
        assertThat(count("item_date")).isEqualTo(7)
        assertThat(match("img_9.jpg")).isEmpty()
        assertThat(longs("SELECT id FROM item_key WHERE id IN (${gone.joinToString()})")).isEmpty()
        assertThat(match("img_1.jpg")).hasSize(1)
    }

    @Test
    fun `reconcile of one source never deletes another source`() {
        upsert(listOf(photo(1), sms(1), call(1), mms(1)))
        val n = runBlocking { writer.reconcile(Source.MEDIA_STORE, longSetOf()) }
        assertThat(n).isEqualTo(1)
        assertThat(items(1)).isEqualTo(0)
        assertThat(items(2)).isEqualTo(1)
        assertThat(items(3)).isEqualTo(1)
        assertThat(items(4)).isEqualTo(1)
        for (s in listOf(Source.CALL_LOG, Source.SMS, Source.MMS)) {
            // same source ids exist in every source; present set {1} for one source leaves the others alone
            assertThat(runBlocking { writer.reconcile(s, longSetOf(1)) }).isEqualTo(0)
        }
        assertThat(count("item")).isEqualTo(3)
    }

    @Test
    fun `an empty present set deletes every item of the source`() {
        upsert(listOf(sms(1), sms(2), call(1)))
        assertThat(runBlocking { writer.reconcile(Source.SMS, longSetOf()) }).isEqualTo(2)
        assertThat(count("item")).isEqualTo(1)
        assertThat(count("fts_short")).isEqualTo(1)
    }

    @Test
    fun `more than 500 stale rows are deleted in chunks`() {
        upsert((1L..1201L).map { photo(it) })
        assertThat(runBlocking { writer.reconcile(Source.MEDIA_STORE, longSetOf(1)) }).isEqualTo(1200)
        assertThat(count("item")).isEqualTo(1)
        assertThat(count("fts_short")).isEqualTo(1)
    }

    @Test
    fun `purge with types removes only those types`() {
        upsert(listOf(photo(1), doc(2), doc(3), recording(4)))
        val n = runBlocking { writer.purge(Source.MEDIA_STORE, setOf(ItemType.DOCUMENT)) }
        assertThat(n).isEqualTo(2)
        assertThat(longs("SELECT type FROM item ORDER BY type")).containsExactly(1L, 6L).inOrder()
        assertThat(match("report_2.pdf")).isEmpty()
    }

    @Test
    fun `purge of a source returns its count and keeps sync state`() {
        upsert(listOf(sms(1), sms(2), sms(3), call(1)))
        runBlocking { writer.saveSyncState(SyncState(Source.SMS, 42, null, null, null, null)) }
        assertThat(runBlocking { writer.purge(Source.SMS) }).isEqualTo(3)
        assertThat(count("item")).isEqualTo(1)
        assertThat(runBlocking { writer.syncState(Source.SMS) }?.cursor).isEqualTo(42L)
        assertThat(runBlocking { writer.purge(Source.SMS, emptySet()) }).isEqualTo(0)
    }

    @Test
    fun `purge handles more than one chunk`() {
        upsert((1L..1201L).map { photo(it) })
        assertThat(runBlocking { writer.purge(Source.MEDIA_STORE) }).isEqualTo(1201)
        assertThat(count("item")).isEqualTo(0)
    }

    @Test
    fun `links of deleted items are gone`() {
        upsert(listOf(call(1), call(2), recording(10), recording(11)))
        val c1 = idOf(Source.CALL_LOG, 1)
        val c2 = idOf(Source.CALL_LOG, 2)
        val r10 = idOf(Source.MEDIA_STORE, 10)
        val r11 = idOf(Source.MEDIA_STORE, 11)
        runBlocking {
            writer.replaceRecordingLinks(
                listOf(RecordingLink(c1, r10, 0.9f, "time"), RecordingLink(c2, r11, 0.8f, "time")),
            )
        }
        assertThat(count("recording_link")).isEqualTo(2)
        runBlocking { writer.reconcile(Source.CALL_LOG, longSetOf(2)) }
        assertThat(longs("SELECT call_item_id FROM recording_link")).containsExactly(c2)
        runBlocking { writer.purge(Source.MEDIA_STORE, setOf(ItemType.RECORDING)) }
        assertThat(count("recording_link")).isEqualTo(0)
    }

    private fun mms(id: Long) = sms(id).copy(key = com.kuriousdragon.smartindex.core.model.SourceKey(Source.MMS, id))
}
