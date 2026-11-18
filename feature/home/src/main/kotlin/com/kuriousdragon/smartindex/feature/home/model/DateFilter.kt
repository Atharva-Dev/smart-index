package com.kuriousdragon.smartindex.feature.home.model

import com.kuriousdragon.smartindex.core.model.DateRange
import java.time.LocalDate
import java.time.ZoneId

/** The Search date filter (Design Spec §6.10). */
sealed interface DateFilter {
    data object AnyDate : DateFilter

    data object Today : DateFilter

    data object Yesterday : DateFilter

    /** today-6 … today (7 days, inclusive). */
    data object Last7Days : DateFilter

    /** today-29 … today. */
    data object Last30Days : DateFilter

    data class Day(val date: LocalDate) : DateFilter

    data class Range(val start: LocalDate, val end: LocalDate) : DateFilter {
        init {
            require(!end.isBefore(start)) { "end must not be before start" }
        }
    }
}

/** Inclusive range of local dates. */
data class DayRange(val first: LocalDate, val last: LocalDate)

/** Presets are relative to [today]; null for [DateFilter.AnyDate]. */
fun DateFilter.resolve(today: LocalDate): DayRange? = when (this) {
    DateFilter.AnyDate -> null
    DateFilter.Today -> DayRange(today, today)
    DateFilter.Yesterday -> today.minusDays(1).let { DayRange(it, it) }
    DateFilter.Last7Days -> DayRange(today.minusDays(6), today)
    DateFilter.Last30Days -> DayRange(today.minusDays(29), today)
    is DateFilter.Day -> DayRange(date, date)
    is DateFilter.Range -> DayRange(start, end)
}

/**
 * Whole first and last day in [zone]: start = first.atStartOfDay(zone),
 * endInclusive = last.plusDays(1).atStartOfDay(zone) - 1 ms (DST-safe).
 */
fun DayRange.toDateRange(zone: ZoneId): DateRange = DateRange(
    startMs = first.atStartOfDay(zone).toInstant().toEpochMilli(),
    endMsInclusive = last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L,
)
