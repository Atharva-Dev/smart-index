package com.kuriousdragon.smartindex.feature.home.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Part of the day by hour of day: 00–04, 05–11, 12–16, 17–21, 22–23 (inclusive). */
enum class PartOfDay {
    EARLY_HOURS,
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT,
    ;

    companion object {
        fun of(hour: Int): PartOfDay = when (hour) {
            in 0..4 -> EARLY_HOURS
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..21 -> EVENING
            in 22..23 -> NIGHT
            else -> throw IllegalArgumentException("hour out of range: $hour")
        }
    }
}

sealed interface ListGroup {
    data class PartOfDayGroup(val part: PartOfDay) : ListGroup

    data class DateGroup(val date: LocalDate) : ListGroup
}

/** How a list is grouped under headers. */
enum class Grouping {
    PART_OF_DAY,
    DATE,
    ;

    fun groupOf(timeMs: Long, zone: ZoneId): ListGroup {
        val zoned = Instant.ofEpochMilli(timeMs).atZone(zone)
        return when (this) {
            PART_OF_DAY -> ListGroup.PartOfDayGroup(PartOfDay.of(zoned.hour))
            DATE -> ListGroup.DateGroup(zoned.toLocalDate())
        }
    }
}
