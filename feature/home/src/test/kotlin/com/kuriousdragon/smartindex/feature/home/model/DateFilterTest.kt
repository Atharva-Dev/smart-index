package com.kuriousdragon.smartindex.feature.home.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertThrows
import org.junit.Test

class DateFilterTest {
    private val today = LocalDate.of(2026, 11, 3)

    @Test
    fun `presets resolve relative to today`() {
        assertThat(DateFilter.AnyDate.resolve(today)).isNull()
        assertThat(DateFilter.Today.resolve(today)).isEqualTo(DayRange(today, today))
        val yesterday = LocalDate.of(2026, 11, 2)
        assertThat(DateFilter.Yesterday.resolve(today)).isEqualTo(DayRange(yesterday, yesterday))
        assertThat(DateFilter.Last7Days.resolve(today)).isEqualTo(DayRange(LocalDate.of(2026, 10, 28), today))
        assertThat(DateFilter.Last30Days.resolve(today)).isEqualTo(DayRange(LocalDate.of(2026, 10, 5), today))
    }

    @Test
    fun `day and range resolve to themselves`() {
        val d = LocalDate.of(2026, 1, 15)
        assertThat(DateFilter.Day(d).resolve(today)).isEqualTo(DayRange(d, d))
        val s = LocalDate.of(2026, 10, 29)
        assertThat(DateFilter.Range(s, today).resolve(today)).isEqualTo(DayRange(s, today))
    }

    @Test
    fun `range accepts a single day and rejects end before start`() {
        DateFilter.Range(today, today)
        assertThrows(IllegalArgumentException::class.java) { DateFilter.Range(today, today.minusDays(1)) }
    }

    @Test
    fun `toDateRange covers a normal day in UTC`() {
        val r = DayRange(today, today).toDateRange(ZoneId.of("UTC"))
        assertThat(r.endMsInclusive - r.startMs).isEqualTo(DAY_MS - 1)
        assertThat(r.startMs).isEqualTo(today.toEpochDay() * DAY_MS)
    }

    @Test
    fun `toDateRange covers a 23 hour DST day in Europe London`() {
        val day = LocalDate.of(2026, 3, 29)
        val r = DayRange(day, day).toDateRange(LONDON)
        assertThat(r.endMsInclusive - r.startMs).isEqualTo(23 * HOUR_MS - 1)
        val nextMidnight = day.plusDays(1).atStartOfDay(LONDON).toInstant().toEpochMilli()
        assertThat(r.endMsInclusive).isEqualTo(nextMidnight - 1)
    }

    @Test
    fun `toDateRange covers a 25 hour DST day in Europe London`() {
        val day = LocalDate.of(2026, 10, 25)
        val r = DayRange(day, day).toDateRange(LONDON)
        assertThat(r.endMsInclusive - r.startMs).isEqualTo(25 * HOUR_MS - 1)
        val nextMidnight = day.plusDays(1).atStartOfDay(LONDON).toInstant().toEpochMilli()
        assertThat(r.endMsInclusive).isEqualTo(nextMidnight - 1)
    }

    @Test
    fun `toDateRange of several days spans first start to last end`() {
        val r = DayRange(today.minusDays(2), today).toDateRange(ZoneId.of("UTC"))
        assertThat(r.endMsInclusive - r.startMs).isEqualTo(3 * DAY_MS - 1)
    }

    private companion object {
        val LONDON: ZoneId = ZoneId.of("Europe/London")
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 24 * HOUR_MS
    }
}
