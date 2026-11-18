package com.kuriousdragon.smartindex.feature.home.format

import java.util.Locale
import kotlin.math.roundToLong

/** 1024-based file sizes: `B`, `KB`, `MB`, `GB`, `TB`; one decimal below 100, none from 100 or for bytes. */
object SizeFormat {
    private val units = listOf("B", "KB", "MB", "GB", "TB")

    fun format(bytes: Long, locale: Locale): String {
        var value = maxOf(bytes, 0L).toDouble()
        var unit = 0
        while (unit < units.lastIndex && (value >= 1024.0 || value.roundToLong() >= 1024L)) {
            value /= 1024.0
            unit++
        }
        val rounded = (value * 10).roundToLong() / 10.0
        val text = if (unit == 0 || rounded >= 100.0) {
            value.roundToLong().toString()
        } else {
            String.format(locale, "%.1f", value)
        }
        return text + " " + units[unit]
    }
}
