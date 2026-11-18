package com.kuriousdragon.smartindex.feature.home.format

import android.content.res.Resources
import com.kuriousdragon.smartindex.core.model.ResultItem
import com.kuriousdragon.smartindex.feature.home.R
import com.kuriousdragon.smartindex.feature.home.model.DateFilter
import com.kuriousdragon.smartindex.feature.home.model.DeviceEnv
import com.kuriousdragon.smartindex.feature.home.model.resolve
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Date text for the Home date button, section headers, the search chip, sheets and the player (LLD 4.3, 4.4). */
object DateLabels {
    private const val DAY_MONTH = "EEE, d MMM"
    private const val DAY_MONTH_YEAR = "EEE, d MMM yyyy"
    private const val SHORT_DATE = "d MMM yyyy"
    private const val FULL_DATE = "EEEE, d MMMM yyyy"
    private const val STAMP = "EEE d MMM"
    private const val EN_DASH = " – "

    private fun fmt(pattern: String, env: DeviceEnv) = DateTimeFormatter.ofPattern(pattern, env.locale)

    /** Home date button and date-section header: `Today · Tue, 3 Nov`, `Yesterday · …`, else `Thu, 29 Oct 2026`. */
    fun header(date: LocalDate, env: DeviceEnv, res: Resources): String = when (date) {
        env.today -> res.getString(R.string.date_today, fmt(DAY_MONTH, env).format(date))
        env.today.minusDays(1) -> res.getString(R.string.date_yesterday, fmt(DAY_MONTH, env).format(date))
        else -> fmt(DAY_MONTH_YEAR, env).format(date)
    }

    /** The date button uses the same rule as the section header. */
    fun homeButton(date: LocalDate, env: DeviceEnv, res: Resources): String = header(date, env, res)

    /** Full date for the TalkBack description of the date button: `Tuesday, 3 November 2026`. */
    fun full(date: LocalDate, env: DeviceEnv): String = fmt(FULL_DATE, env).format(date)

    /** Search chip label: single day (year only when it is not this year), ranges, and the Last N days presets. */
    fun chip(filter: DateFilter, env: DeviceEnv, res: Resources): String = when (filter) {
        DateFilter.AnyDate -> res.getString(R.string.date_any)

        DateFilter.Last7Days -> res.getString(R.string.date_last_7_days)

        DateFilter.Last30Days -> res.getString(R.string.date_last_30_days)

        else -> {
            val range = checkNotNull(filter.resolve(env.today))
            if (range.first == range.last) singleDay(range.first, env) else range(range.first, range.last, env)
        }
    }

    private fun singleDay(date: LocalDate, env: DeviceEnv): String {
        val pattern = if (date.year == env.today.year) DAY_MONTH else DAY_MONTH_YEAR
        return fmt(pattern, env).format(date)
    }

    private fun range(first: LocalDate, last: LocalDate, env: DeviceEnv): String = when {
        first.year != last.year -> fmt(SHORT_DATE, env).format(first) + EN_DASH + fmt(SHORT_DATE, env).format(last)

        first.month != last.month ->
            fmt("d MMM", env).format(first) + EN_DASH + fmt(SHORT_DATE, env).format(last)

        else -> fmt("d", env).format(first) + EN_DASH + fmt(SHORT_DATE, env).format(last)
    }

    /** `Tue 3 Nov, 09:15`: file-sheet header and player subtitle. */
    fun stamp(timeMs: Long, env: DeviceEnv): String {
        val date = java.time.Instant.ofEpochMilli(timeMs).atZone(env.zone)
        return fmt(STAMP, env).format(date) + ", " + TimeLabels.format(timeMs, env)
    }

    /** File sheet header: `Download · 240 KB · Tue 3 Nov, 09:15` (missing parts are left out). */
    fun fileSheetHeader(item: ResultItem, env: DeviceEnv): String = listOfNotNull(
        RowFormatter.folderSegment(item.folder)?.first,
        item.sizeBytes?.let { SizeFormat.format(it, env.locale) },
        stamp(item.time, env),
    ).joinToString(" · ")
}
