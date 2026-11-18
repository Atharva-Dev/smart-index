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
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** `IndexQuery.search` with a date range (LLD §7.3). */
class SearchRangeTest : IndexDbTest() {
    private val d1 = LocalDate.of(2026, 3, 10)
    private val d2 = d1.plusDays(1)
    private val d3 = d1.plusDays(2)

    private fun write(vararg r: ItemRecord) = runBlocking { writer.upsert(r.toList()) }

    private fun range(from: LocalDate, to: LocalDate) =
        DateRange(at(from, 0, 0, 0, 0), at(to, 23, 59, 59, 999))

    private fun docWith(id: Long, title: String, vararg dates: Pair<DateKind, Long>) =
        ItemRecord(
            key = SourceKey(Source.MEDIA_STORE, id),
            type = ItemType.DOCUMENT,
            title = title,
            primaryTs = dates.first().second,
            dates = dates.toMap(),
            folder = "Download",
            ext = "pdf",
            sourceVersion = 1,
        )

    @Test
    fun `text with a range returns items with any date inside once, newest first, at the latest date in range`() {
        write(
            docWith(1, "invoice a.pdf", DateKind.ADDED to at(d1, 9), DateKind.MODIFIED to at(d2, 10)),
            docWith(2, "invoice b.pdf", DateKind.ADDED to at(d1, 11)),
            docWith(3, "invoice c.pdf", DateKind.ADDED to at(d3, 9)),
        )
        val hits = query.search(SearchQuery("invoice", range(d1, d2), null)).all()
        assertThat(hits.map { it.title }).containsExactly("invoice a.pdf", "invoice b.pdf").inOrder()
        assertThat(hits.map { it.time }).containsExactly(at(d2, 10), at(d1, 11)).inOrder()
        assertThat(query.search(SearchQuery("invoice", range(d1, d1), null)).all().map { it.time })
            .containsExactly(at(d1, 11), at(d1, 9)).inOrder()
    }

    @Test
    fun `a short query with a range behaves the same`() {
        write(
            docWith(1, "ab one.pdf", DateKind.ADDED to at(d1, 9)),
            docWith(2, "ab two.pdf", DateKind.ADDED to at(d3, 9)),
        )
        val hits = query.search(SearchQuery("ab", range(d1, d2), null)).all()
        assertThat(hits.map { it.title }).containsExactly("ab one.pdf")
        assertThat(hits.single().time).isEqualTo(at(d1, 9))
    }

    @Test
    fun `blank text with a range lists in timeline order at the earliest date`() {
        write(
            docWith(1, "a.pdf", DateKind.ADDED to at(d1, 9), DateKind.MODIFIED to at(d2, 10)),
            smsAt(1, at(d1, 8)),
            smsAt(2, at(d3, 8)),
        )
        val hits = query.search(SearchQuery("", range(d1, d2), null)).all()
        assertThat(hits.map { it.time }).containsExactly(at(d1, 8), at(d1, 9)).inOrder()
        assertThat(hits.all { it.matches.isEmpty() }).isTrue()
    }

    @Test
    fun `type and range combine`() {
        write(smsAt(1, at(d1, 8), body = "invoice sent"), docWith(1, "invoice.pdf", DateKind.ADDED to at(d1, 9)))
        assertThat(query.search(SearchQuery("invoice", range(d1, d1), ItemType.MESSAGE)).all().map { it.type })
            .containsExactly(ItemType.MESSAGE)
        assertThat(query.search(SearchQuery("", range(d1, d1), ItemType.DOCUMENT)).all().map { it.type })
            .containsExactly(ItemType.DOCUMENT)
    }

    @Test
    fun `range bounds are inclusive`() {
        write(smsAt(1, 5_000), smsAt(2, 6_000), smsAt(3, 7_000))
        val hits = query.search(SearchQuery("hotel", DateRange(5_000, 6_000), null)).all()
        assertThat(hits.map { it.time }).containsExactly(6_000L, 5_000L).inOrder()
    }
}
