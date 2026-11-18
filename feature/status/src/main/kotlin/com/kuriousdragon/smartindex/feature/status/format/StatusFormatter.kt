package com.kuriousdragon.smartindex.feature.status.format

import com.kuriousdragon.smartindex.feature.status.model.StatusEnv
import java.text.NumberFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Pure text formatting for the Index status screen (LLD §3.5); JVM-testable. */
class StatusFormatter(private val env: StatusEnv, private val nowMs: Long) {
    /** Locale grouping of [n], for example "18,240". */
    fun count(n: Int): String = NumberFormat.getIntegerInstance(env.locale).format(n)

    /** Classifies [ms] against the calendar day of `nowMs` in the environment zone. */
    fun lastRun(ms: Long): LastRun {
        val pattern = if (env.is24Hour) "HH:mm" else "h:mm a"
        val time = DateTimeFormatter.ofPattern(pattern, env.locale).withZone(env.zone).format(Instant.ofEpochMilli(ms))
        val day = Instant.ofEpochMilli(ms).atZone(env.zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMs).atZone(env.zone).toLocalDate()
        return when (day) {
            today -> LastRun.Today(time)

            today.minusDays(1) -> LastRun.Yesterday(time)

            else -> LastRun.Other(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(env.locale).withZone(env.zone)
                    .format(Instant.ofEpochMilli(ms)),
                time,
            )
        }
    }

    /** Rounds down to a multiple of 10 so TalkBack speaks progress at most every 10 %. */
    fun announcePercent(percent: Int): Int = (percent / 10) * 10
}

sealed interface LastRun {
    data class Today(val time: String) : LastRun

    data class Yesterday(val time: String) : LastRun

    /** [date] is `FormatStyle.MEDIUM` in the environment locale. */
    data class Other(val date: String, val time: String) : LastRun
}

/** 0 items: "", 1: "X", 2: "X and Y", 3 or more: "X, Y and Z". */
fun joinWords(items: List<String>, and: String): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> items.dropLast(1).joinToString(", ") + " $and " + items.last()
}
