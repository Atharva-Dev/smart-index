package com.kuriousdragon.smartindex.core.index.query

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.index.IndexDbTest
import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.core.model.ItemRecord
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.ResultItem
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceKey
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** `IndexQuery.timeline` (LLD §7.3): which items are "on" a day, in what order, in which zone. */
class TimelineTest : IndexDbTest() {
    private val d1 = LocalDate.of(2026, 3, 10)
    private val d2 = d1.plusDays(1)

    private fun write(vararg r: ItemRecord) = runBlocking { writer.upsert(r.toList()) }

    private fun day(d: LocalDate, type: ItemType? = null): List<ResultItem> = query.timeline(d, type).all()

    private fun docWith(id: Long, vararg dates: Pair<DateKind, Long>) =
        ItemRecord(
            key = SourceKey(Source.MEDIA_STORE, id),
            type = ItemType.DOCUMENT,
            title = "doc$id.pdf",
            primaryTs = dates.first().second,
            dates = dates.toMap(),
            folder = "Download",
            ext = "pdf",
            sourceVersion = 1,
        )

    @Test
    fun `items are listed oldest to newest`() {
        write(smsAt(1, at(d1, 18)), smsAt(2, at(d1, 8)), smsAt(3, at(d1, 13)))
        val items = day(d1)
        assertThat(items.map { it.time }).containsExactly(at(d1, 8), at(d1, 13), at(d1, 18)).inOrder()
        assertThat(items.map { it.id }).containsExactly(idOf(Source.SMS, 2), idOf(Source.SMS, 3), idOf(Source.SMS, 1))
            .inOrder()
    }

    @Test
    fun `messages carry the start of their text and no matches`() {
        write(smsAt(1, at(d1), body = "Hello there"))
        val item = day(d1).single()
        assertThat(item.snippet).isEqualTo("Hello there")
        assertThat(item.matches).isEmpty()
    }

    @Test
    fun `a document added on one day and modified on the next shows on both days once each`() {
        write(docWith(1, DateKind.ADDED to at(d1, 9), DateKind.MODIFIED to at(d2, 10)))
        assertThat(day(d1).map { it.time }).containsExactly(at(d1, 9))
        assertThat(day(d2).map { it.time }).containsExactly(at(d2, 10))
    }

    @Test
    fun `added and modified on the same day shows once at the earlier time`() {
        write(docWith(1, DateKind.ADDED to at(d1, 9), DateKind.MODIFIED to at(d1, 15)))
        assertThat(day(d1).map { it.time }).containsExactly(at(d1, 9))
    }

    @Test
    fun `a photo is only on the day it was taken`() {
        write(photo(1, taken = at(d1, 10), added = at(d2, 10)))
        assertThat(day(d1)).hasSize(1)
        assertThat(day(d2)).isEmpty()
    }

    @Test
    fun `a recording is on its event day`() {
        val rec = recording(1).copy(
            primaryTs = at(d1, 20),
            dates = mapOf(
                DateKind.EVENT to at(d1, 20),
                DateKind.MODIFIED to at(d2, 1),
            ),
        )
        write(rec)
        assertThat(day(d1).single().type).isEqualTo(ItemType.RECORDING)
        assertThat(day(d2)).isEmpty()
    }

    @Test
    fun `the type filter keeps only that type`() {
        write(smsAt(1, at(d1, 8)), photo(2, taken = at(d1, 9), added = null))
        assertThat(day(d1)).hasSize(2)
        assertThat(day(d1, ItemType.PHOTO).map { it.type }).containsExactly(ItemType.PHOTO)
        assertThat(day(d1, ItemType.MESSAGE).map { it.type }).containsExactly(ItemType.MESSAGE)
        assertThat(day(d1, ItemType.CALL)).isEmpty()
    }

    @Test
    fun `day boundaries include 00 00 00 000 and 23 59 59 999 and exclude the next midnight`() {
        write(
            smsAt(1, at(d1, 0, 0, 0, 0)),
            smsAt(2, at(d1, 23, 59, 59, 999)),
            smsAt(3, at(d2, 0, 0, 0, 0)),
            smsAt(4, at(d1, 0, 0, 0, 0) - 1),
        )
        assertThat(day(d1).map { it.id })
            .containsExactly(idOf(Source.SMS, 1), idOf(Source.SMS, 2)).inOrder()
        assertThat(day(d2).map { it.id }).containsExactly(idOf(Source.SMS, 3))
    }

    @Test
    fun `DST days of Europe London have their true length`() {
        zone = ZoneId.of("Europe/London")
        val spring = LocalDate.of(2026, 3, 29)
        val autumn = LocalDate.of(2026, 10, 25)
        write(
            smsAt(1, at(spring, 0, 30)),
            smsAt(2, at(spring, 23, 59, 59, 999)),
            smsAt(3, at(spring.plusDays(1), 0, 0)),
            smsAt(4, at(autumn, 0, 30)),
            smsAt(5, at(autumn, 23, 59, 59, 999)),
            smsAt(6, at(autumn.plusDays(1), 0, 0)),
        )
        assertThat(day(spring).map { it.id }).containsExactly(idOf(Source.SMS, 1), idOf(Source.SMS, 2)).inOrder()
        assertThat(day(autumn).map { it.id }).containsExactly(idOf(Source.SMS, 4), idOf(Source.SMS, 5)).inOrder()
        assertThat(day(autumn.plusDays(1)).map { it.id }).containsExactly(idOf(Source.SMS, 6))
    }

    @Test
    fun `changing the time zone moves an item to another day without re-indexing`() {
        // 2026-03-10 20:00 UTC = 2026-03-11 01:30 in Kolkata.
        val ts = java.time.ZonedDateTime.of(2026, 3, 10, 20, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        write(smsAt(1, ts))
        zone = ZoneId.of("UTC")
        assertThat(day(LocalDate.of(2026, 3, 10))).hasSize(1)
        assertThat(day(LocalDate.of(2026, 3, 11))).isEmpty()
        zone = ZoneId.of("Asia/Kolkata")
        assertThat(day(LocalDate.of(2026, 3, 10))).isEmpty()
        assertThat(day(LocalDate.of(2026, 3, 11))).hasSize(1)
    }
}
