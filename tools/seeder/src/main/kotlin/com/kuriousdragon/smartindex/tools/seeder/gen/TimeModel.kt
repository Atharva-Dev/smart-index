package com.kuriousdragon.smartindex.tools.seeder.gen

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Time distribution of the generated items (LLD §4.3, §4.5). All times are local date-times in [zone]. */
class TimeModel(val anchor: LocalDateTime, val zone: ZoneId) {
    val anchorDate: LocalDate = anchor.toLocalDate()
    val busyDay: LocalDate = anchorDate.minusDays(1)
    val hotelDay: LocalDate = anchorDate.minusDays(HOTEL_DAYS_BACK)
    val dayB: LocalDate = anchorDate.minusDays(DAY_B_DAYS_BACK)

    /** Day of the §4.6 tricky recording cases; bulk calls never land on it. */
    val trickyDay: LocalDate = anchorDate.minusDays(TRICKY_DAYS_BACK)

    /** Random time over [anchor − 3 years, anchor]: 20 % last 30 days, 50 % days 31–365, 30 % older. */
    fun random(rng: SeedRandom): LocalDateTime {
        while (true) {
            val u = rng.nextDouble()
            val days = when {
                u < 0.20 -> rng.nextInt(0, 29)
                u < 0.70 -> rng.nextInt(30, 364)
                else -> rng.nextInt(365, 3 * 365 - 1)
            }
            val t = onDay(rng, anchorDate.minusDays(days.toLong()))
            if (!t.isAfter(anchor)) return t
        }
    }

    /** Random time on [day]; hours 00–06 carry 3 %, 07–22 the bulk, 23 a small rest. */
    fun onDay(rng: SeedRandom, day: LocalDate): LocalDateTime {
        while (true) {
            val u = rng.nextDouble()
            val hour = when {
                u < 0.03 -> rng.nextInt(0, 6)
                u < 0.99 -> rng.nextInt(7, 22)
                else -> 23
            }
            val t = LocalDateTime.of(day, LocalTime.of(hour, rng.nextInt(60), rng.nextInt(60)))
            if (!t.isAfter(anchor)) return t
        }
    }

    fun ms(t: LocalDateTime): Long = ZonedDateTime.ofLocal(t, zone, null).toInstant().toEpochMilli()

    fun local(ms: Long): LocalDateTime = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), zone)

    companion object {
        const val HOTEL_DAYS_BACK = 40L
        const val DAY_B_DAYS_BACK = 3L
        const val TRICKY_DAYS_BACK = 5L
    }
}
