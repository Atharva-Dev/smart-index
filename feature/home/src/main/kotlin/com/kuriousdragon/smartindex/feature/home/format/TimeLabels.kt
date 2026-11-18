package com.kuriousdragon.smartindex.feature.home.format

import com.kuriousdragon.smartindex.feature.home.model.DeviceEnv
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Time of day text: `HH:mm` on a 24 h phone, `h:mm a` on a 12 h phone. */
object TimeLabels {
    fun format(timeMs: Long, env: DeviceEnv): String {
        val zoned = Instant.ofEpochMilli(timeMs).atZone(env.zone)
        return if (env.is24Hour) {
            DateTimeFormatter.ofPattern("HH:mm", env.locale).format(zoned)
        } else {
            // Some locales (en-GB) render the marker in lower case; the copy deck shows "12:05 AM".
            DateTimeFormatter.ofPattern("h:mm a", env.locale).format(zoned).uppercase(env.locale)
        }
    }
}
