package com.kuriousdragon.smartindex.feature.status.model

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.ZoneId
import java.util.Locale

/** Locale, zone and clock style used to format counts and times (LLD §3.5). */
data class StatusEnv(val zone: ZoneId, val locale: Locale, val is24Hour: Boolean)

/** Reads the environment; recomposes on a locale change through `LocalConfiguration`. */
@Composable
fun rememberStatusEnv(): StatusEnv {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    return remember(configuration, locale) {
        StatusEnv(ZoneId.systemDefault(), locale, DateFormat.is24HourFormat(context))
    }
}
