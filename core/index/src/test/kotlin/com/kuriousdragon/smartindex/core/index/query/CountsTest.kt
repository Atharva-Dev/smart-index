package com.kuriousdragon.smartindex.core.index.query

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.index.IndexDbTest
import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.core.model.DateRange
import com.kuriousdragon.smartindex.core.model.ItemRecord
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.SearchQuery
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceKey
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

/** `IndexQuery.counts` (LLD §7.3). */
class CountsTest : IndexDbTest() {
    private val day = LocalDate.of(2026, 3, 10)
    private val next = day.plusDays(1)

    private fun range(from: LocalDate, to: LocalDate) =
        DateRange(at(from, 0, 0, 0, 0), at(to, 23, 59, 59, 999))

    private fun file(id: Long, type: ItemType, title: String, vararg dates: Pair<DateKind, Long>) =
        ItemRecord(
            key = SourceKey(Source.MEDIA_STORE, id),
            type = type,
            title = title,
            primaryTs = dates.first().second,
            dates = dates.toMap(),
            folder = "Download",
            ext = "bin",
            sourceVersion = 1,
        )

    private fun seed() = runBlocking {
        writer.upsert(
            listOf(
                file(
                    1,
                    ItemType.DOCUMENT,
                    "invoice a.pdf",
                    DateKind.ADDED to at(day, 9),
                    DateKind.MODIFIED to at(day, 15),
                ),
                file(2, ItemType.DOCUMENT, "invoice b.pdf", DateKind.ADDED to at(next, 9)),
                file(3, ItemType.PHOTO, "invoice photo.jpg", DateKind.TAKEN to at(day, 10)),
                file(4, ItemType.VIDEO, "clip.mp4", DateKind.TAKEN to at(day, 11)),
                file(5, ItemType.OTHER_FILE, "invoice data.bin", DateKind.ADDED to at(next, 12)),
                smsAt(1, at(day, 8), body = "invoice sent"),
                smsAt(2, at(next, 8), body = "invoice again"),
                smsAt(3, at(day, 20), body = "hello"),
            ),
        )
    }

    @Test
    fun `counts for a day are distinct items per type with all 7 keys`() {
        seed()
        val c = runBlocking { query.counts(day) }
        assertThat(c.keys).containsExactlyElementsIn(ItemType.entries)
        assertThat(c[ItemType.DOCUMENT]).isEqualTo(1)
        assertThat(c[ItemType.PHOTO]).isEqualTo(1)
        assertThat(c[ItemType.VIDEO]).isEqualTo(1)
        assertThat(c[ItemType.MESSAGE]).isEqualTo(2)
        assertThat(c[ItemType.CALL]).isEqualTo(0)
        assertThat(c[ItemType.RECORDING]).isEqualTo(0)
        assertThat(c[ItemType.OTHER_FILE]).isEqualTo(0)
    }

    @Test
    fun `counts for a query has all 7 keys and ignores the type`() {
        seed()
        val all = runBlocking { query.counts(SearchQuery("invoice", null, null)) }
        val typed = runBlocking { query.counts(SearchQuery("invoice", null, ItemType.PHOTO)) }
        assertThat(all.keys).containsExactlyElementsIn(ItemType.entries)
        assertThat(typed).isEqualTo(all)
        assertThat(all[ItemType.DOCUMENT]).isEqualTo(2)
        assertThat(all[ItemType.MESSAGE]).isEqualTo(2)
        assertThat(all[ItemType.VIDEO]).isEqualTo(0)
    }

    @Test
    fun `the sum of counts equals the rows paged for plans A to E`() {
        seed()
        val queries =
            listOf(
                SearchQuery("invoice", null, null), // A
                SearchQuery("invoice", range(day, next), null), // B
                SearchQuery("in", null, null), // C
                SearchQuery("in", range(day, day), null), // D
                SearchQuery("", range(day, next), null), // E
            )
        for (q in queries) {
            val counted = runBlocking { query.counts(q) }.values.sum()
            val paged = query.search(q).all().size
            assertThat(counted).isEqualTo(paged)
            assertThat(paged).isGreaterThan(0)
        }
    }

    @Test
    fun `an item with two dates in the range is counted once`() {
        seed()
        val c = runBlocking { query.counts(SearchQuery("invoice", range(day, day), null)) }
        assertThat(c[ItemType.DOCUMENT]).isEqualTo(1)
    }

    @Test
    fun `empty query counts are all zeros`() {
        seed()
        val c = runBlocking { query.counts(SearchQuery("", null, null)) }
        assertThat(c.keys).containsExactlyElementsIn(ItemType.entries)
        assertThat(c.values.sum()).isEqualTo(0)
    }

    @Test
    fun `a cancelled counts job throws CancellationException`() {
        seed()
        runBlocking {
            val job = async(start = CoroutineStart.LAZY) { query.counts(SearchQuery("invoice", null, null)) }
            job.cancel()
            assertThrows(CancellationException::class.java) { runBlocking { job.await() } }
        }
        // Nothing leaks: the database still answers.
        assertThat(runBlocking { query.counts(day) }[ItemType.PHOTO]).isEqualTo(1)
    }
}
