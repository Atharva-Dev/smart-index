package com.kuriousdragon.smartindex.core.index.query

import com.kuriousdragon.smartindex.core.model.DateRange
import java.time.LocalDate
import java.time.ZoneId

/** Inclusive epoch-ms bounds of a local calendar day (LLD §5.5); DST days get their true 23 h / 25 h length. */
object DayBounds {
    fun of(day: LocalDate, zone: ZoneId): DateRange =
        DateRange(
            startMs = day.atStartOfDay(zone).toInstant().toEpochMilli(),
            endMsInclusive = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1,
        )
}
