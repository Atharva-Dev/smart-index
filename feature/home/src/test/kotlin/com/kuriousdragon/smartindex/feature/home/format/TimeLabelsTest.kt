package com.kuriousdragon.smartindex.feature.home.format

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimeLabelsTest {
    private fun at(time: String) = londonMs("2026-11-03", time)

    @Test
    fun `24 hour phones show HH mm`() {
        val env = testEnv(is24Hour = true)
        assertThat(TimeLabels.format(at("09:15"), env)).isEqualTo("09:15")
        assertThat(TimeLabels.format(at("23:59"), env)).isEqualTo("23:59")
        assertThat(TimeLabels.format(at("00:00"), env)).isEqualTo("00:00")
    }

    @Test
    fun `12 hour phones show h mm with an AM or PM marker`() {
        val env = testEnv(is24Hour = false)
        assertThat(TimeLabels.format(at("09:15"), env)).isEqualTo("9:15 AM")
        assertThat(TimeLabels.format(at("00:05"), env)).isEqualTo("12:05 AM")
        assertThat(TimeLabels.format(at("12:00"), env)).isEqualTo("12:00 PM")
        assertThat(TimeLabels.format(at("23:59"), env)).isEqualTo("11:59 PM")
    }

    @Test
    fun `the time follows the zone of the environment`() {
        val summer = java.time.Instant.parse("2026-07-01T08:15:00Z").toEpochMilli() // BST, UTC+1
        assertThat(TimeLabels.format(summer, testEnv())).isEqualTo("09:15")
    }
}
