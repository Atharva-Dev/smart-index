package com.kuriousdragon.smartindex.feature.status

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.feature.status.format.LastRun
import com.kuriousdragon.smartindex.feature.status.format.StatusFormatter
import com.kuriousdragon.smartindex.feature.status.format.joinWords
import com.kuriousdragon.smartindex.feature.status.model.StatusEnv
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Test

class StatusFormatterTest {
    private val utc = ZoneId.of("UTC")

    private fun ms(zone: ZoneId, y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private fun fmt(
        nowMs: Long,
        zone: ZoneId = utc,
        locale: Locale = Locale.US,
        is24: Boolean = true,
    ) = StatusFormatter(StatusEnv(zone, locale, is24), nowMs)

    private val now = ms(utc, 2026, 11, 5, 15, 0)

    @Test
    fun `count groups digits for US and India locales`() {
        for (locale in listOf(Locale.US, Locale.forLanguageTag("en-IN"))) {
            val f = fmt(now, locale = locale)
            assertThat(f.count(0)).isEqualTo("0")
            assertThat(f.count(999)).isEqualTo("999")
            assertThat(f.count(18240)).isEqualTo("18,240")
        }
    }

    @Test
    fun `same day is Today with 24 hour time`() {
        assertThat(fmt(now).lastRun(ms(utc, 2026, 11, 5, 9, 42))).isEqualTo(LastRun.Today("09:42"))
    }

    @Test
    fun `same day is Today with 12 hour time`() {
        assertThat(fmt(now, is24 = false).lastRun(ms(utc, 2026, 11, 5, 9, 42))).isEqualTo(LastRun.Today("9:42 AM"))
    }

    @Test
    fun `previous calendar day is Yesterday`() {
        assertThat(fmt(now).lastRun(ms(utc, 2026, 11, 4, 23, 59))).isEqualTo(LastRun.Yesterday("23:59"))
    }

    @Test
    fun `older is Other with a medium date`() {
        val r = fmt(now).lastRun(ms(utc, 2026, 11, 1, 8, 5))
        assertThat(r).isEqualTo(LastRun.Other("Nov 1, 2026", "08:05"))
    }

    @Test
    fun `day comparison uses the environment zone across midnight`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val la = ZoneId.of("America/Los_Angeles")
        // 2026-11-05 16:30 UTC is Nov 6 01:30 in Tokyo and Nov 5 08:30 in Los Angeles.
        val last = ms(utc, 2026, 11, 5, 16, 30)
        val nowTokyo = ms(tokyo, 2026, 11, 6, 10, 0)
        assertThat(fmt(nowTokyo, tokyo).lastRun(last)).isEqualTo(LastRun.Today("01:30"))
        val nowLa = ms(la, 2026, 11, 6, 10, 0)
        assertThat(fmt(nowLa, la).lastRun(last)).isEqualTo(LastRun.Yesterday("08:30"))
    }

    @Test
    fun `announcePercent rounds down to tens`() {
        val f = fmt(now)
        assertThat(listOf(0, 9, 10, 37, 100).map { f.announcePercent(it) }).containsExactly(0, 0, 10, 30, 100).inOrder()
    }

    @Test
    fun `joinWords joins zero to four items`() {
        assertThat(joinWords(emptyList(), "and")).isEqualTo("")
        assertThat(joinWords(listOf("A"), "and")).isEqualTo("A")
        assertThat(joinWords(listOf("A", "B"), "and")).isEqualTo("A and B")
        assertThat(joinWords(listOf("A", "B", "C"), "and")).isEqualTo("A, B and C")
        assertThat(joinWords(listOf("A", "B", "C", "D"), "and")).isEqualTo("A, B, C and D")
    }
}
