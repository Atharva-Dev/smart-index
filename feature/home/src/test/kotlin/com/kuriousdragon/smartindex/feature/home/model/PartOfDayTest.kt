package com.kuriousdragon.smartindex.feature.home.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertThrows
import org.junit.Test

class PartOfDayTest {
    @Test
    fun `part of day boundaries`() {
        assertThat(PartOfDay.of(0)).isEqualTo(PartOfDay.EARLY_HOURS)
        assertThat(PartOfDay.of(4)).isEqualTo(PartOfDay.EARLY_HOURS)
        assertThat(PartOfDay.of(5)).isEqualTo(PartOfDay.MORNING)
        assertThat(PartOfDay.of(11)).isEqualTo(PartOfDay.MORNING)
        assertThat(PartOfDay.of(12)).isEqualTo(PartOfDay.AFTERNOON)
        assertThat(PartOfDay.of(16)).isEqualTo(PartOfDay.AFTERNOON)
        assertThat(PartOfDay.of(17)).isEqualTo(PartOfDay.EVENING)
        assertThat(PartOfDay.of(21)).isEqualTo(PartOfDay.EVENING)
        assertThat(PartOfDay.of(22)).isEqualTo(PartOfDay.NIGHT)
        assertThat(PartOfDay.of(23)).isEqualTo(PartOfDay.NIGHT)
    }

    @Test
    fun `hour outside 0 to 23 is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { PartOfDay.of(24) }
        assertThrows(IllegalArgumentException::class.java) { PartOfDay.of(-1) }
    }

    @Test
    fun `PART_OF_DAY grouping uses the local hour of the zone`() {
        val t = utcMs(2026, 11, 3, 23, 30)
        assertThat(Grouping.PART_OF_DAY.groupOf(t, UTC)).isEqualTo(ListGroup.PartOfDayGroup(PartOfDay.NIGHT))
        assertThat(Grouping.PART_OF_DAY.groupOf(t, TOKYO)).isEqualTo(ListGroup.PartOfDayGroup(PartOfDay.MORNING))
    }

    @Test
    fun `DATE grouping lands a time near midnight on the right date in two zones`() {
        val t = utcMs(2026, 11, 3, 23, 30)
        assertThat(Grouping.DATE.groupOf(t, UTC)).isEqualTo(ListGroup.DateGroup(LocalDate.of(2026, 11, 3)))
        assertThat(Grouping.DATE.groupOf(t, TOKYO)).isEqualTo(ListGroup.DateGroup(LocalDate.of(2026, 11, 4)))
        val u = utcMs(2026, 11, 4, 0, 30)
        assertThat(Grouping.DATE.groupOf(u, UTC)).isEqualTo(ListGroup.DateGroup(LocalDate.of(2026, 11, 4)))
        assertThat(Grouping.DATE.groupOf(u, NEW_YORK)).isEqualTo(ListGroup.DateGroup(LocalDate.of(2026, 11, 3)))
    }

    private fun utcMs(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        LocalDate.of(y, m, d).atTime(h, min).atZone(UTC).toInstant().toEpochMilli()

    private companion object {
        val UTC: ZoneId = ZoneId.of("UTC")
        val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")
        val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
    }
}
