package com.kuriousdragon.smartindex.core.index.query

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class DayBoundsTest {
    private val hourMs = 3_600_000L

    @Test
    fun `Kolkata day is 24 hours starting at local midnight`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val r = DayBounds.of(LocalDate.of(2026, 11, 3), zone)
        assertThat(r.endMsInclusive - r.startMs + 1).isEqualTo(24 * hourMs)
        assertThat(
            Instant.ofEpochMilli(r.startMs).atZone(zone).toLocalDateTime().toString(),
        ).isEqualTo("2026-11-03T00:00")
        assertThat(Instant.ofEpochMilli(r.endMsInclusive).atZone(zone).toLocalDateTime().toString())
            .isEqualTo("2026-11-03T23:59:59.999")
    }

    @Test
    fun `London spring-forward day is 23 hours`() {
        val zone = ZoneId.of("Europe/London")
        val r = DayBounds.of(LocalDate.of(2026, 3, 29), zone)
        assertThat(r.endMsInclusive - r.startMs + 1).isEqualTo(23 * hourMs)
        assertThat(Instant.ofEpochMilli(r.startMs).atZone(zone).toLocalTime().toString()).isEqualTo("00:00")
    }

    @Test
    fun `London fall-back day is 25 hours`() {
        val zone = ZoneId.of("Europe/London")
        val r = DayBounds.of(LocalDate.of(2026, 10, 25), zone)
        assertThat(r.endMsInclusive - r.startMs + 1).isEqualTo(25 * hourMs)
        assertThat(Instant.ofEpochMilli(r.startMs).atZone(zone).toLocalTime().toString()).isEqualTo("00:00")
    }

    @Test
    fun `consecutive days are contiguous`() {
        val zone = ZoneId.of("Europe/London")
        val a = DayBounds.of(LocalDate.of(2026, 3, 28), zone)
        val b = DayBounds.of(LocalDate.of(2026, 3, 29), zone)
        assertThat(b.startMs).isEqualTo(a.endMsInclusive + 1)
    }
}
