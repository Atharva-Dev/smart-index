package com.kuriousdragon.smartindex.feature.home.format

import android.content.res.Resources
import com.kuriousdragon.smartindex.feature.home.R
import java.util.Locale

object DurationFormat {
    /** Call duration: `X min Y s`, `X min` for whole minutes, `Y s` under a minute, `0 s` for none. */
    fun call(durationMs: Long, res: Resources): String {
        val total = maxOf(durationMs, 0L) / 1000
        val minutes = (total / 60).toInt()
        val seconds = (total % 60).toInt()
        return when {
            minutes == 0 -> res.getString(R.string.duration_sec, seconds)
            seconds == 0 -> res.getString(R.string.duration_min, minutes)
            else -> res.getString(R.string.duration_min_sec, minutes, seconds)
        }
    }

    /** Media and recording duration: `m:ss`, or `h:mm:ss` from one hour. */
    fun media(durationMs: Long): String {
        val total = maxOf(durationMs, 0L) / 1000
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }
}
