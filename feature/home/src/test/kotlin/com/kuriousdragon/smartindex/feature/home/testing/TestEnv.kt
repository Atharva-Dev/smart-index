package com.kuriousdragon.smartindex.feature.home.testing

import com.kuriousdragon.smartindex.feature.home.model.DeviceEnv
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Europe/London, Locale.UK, 24 h, fixed today = Tue 2026-11-03 (a GMT day, London equals UTC). */
internal object TestEnv {
    val zone: ZoneId = ZoneId.of("Europe/London")
    val today: LocalDate = LocalDate.of(2026, 11, 3)
    val env = DeviceEnv(zone, Locale.UK, is24Hour = true, today = today)
}
