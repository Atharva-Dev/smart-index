package com.kuriousdragon.smartindex.feature.home.format

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.feature.home.model.DateFilter
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DateLabelsTest {
    private val res = resources()
    private val env = testEnv()

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    fun `header and home button name today and yesterday`() {
        assertThat(DateLabels.header(d(2026, 11, 3), env, res)).isEqualTo("Today · Tue, 3 Nov")
        assertThat(DateLabels.header(d(2026, 11, 2), env, res)).isEqualTo("Yesterday · Mon, 2 Nov")
        assertThat(DateLabels.homeButton(d(2026, 11, 3), env, res)).isEqualTo("Today · Tue, 3 Nov")
    }

    @Test
    fun `other days show the year`() {
        assertThat(DateLabels.header(d(2026, 10, 29), env, res)).isEqualTo("Thu, 29 Oct 2026")
        assertThat(DateLabels.homeButton(d(2026, 11, 1), env, res)).isEqualTo("Sun, 1 Nov 2026")
    }

    @Test
    fun `today moves with the environment`() {
        val tomorrow = env.copy(today = d(2026, 11, 4))
        assertThat(DateLabels.header(d(2026, 11, 3), tomorrow, res)).isEqualTo("Yesterday · Tue, 3 Nov")
    }

    @Test
    fun `full date for the TalkBack description`() {
        assertThat(DateLabels.full(d(2026, 11, 3), env)).isEqualTo("Tuesday, 3 November 2026")
    }

    @Test
    fun `chip for any date and the Last N days presets`() {
        assertThat(DateLabels.chip(DateFilter.AnyDate, env, res)).isEqualTo("Any date")
        assertThat(DateLabels.chip(DateFilter.Last7Days, env, res)).isEqualTo("Last 7 days")
        assertThat(DateLabels.chip(DateFilter.Last30Days, env, res)).isEqualTo("Last 30 days")
    }

    @Test
    fun `chip for a single day drops the year only in the current year`() {
        assertThat(DateLabels.chip(DateFilter.Today, env, res)).isEqualTo("Tue, 3 Nov")
        assertThat(DateLabels.chip(DateFilter.Yesterday, env, res)).isEqualTo("Mon, 2 Nov")
        assertThat(DateLabels.chip(DateFilter.Day(d(2026, 11, 1)), env, res)).isEqualTo("Sun, 1 Nov")
        val in2027 = env.copy(today = d(2027, 1, 5))
        assertThat(DateLabels.chip(DateFilter.Day(d(2026, 11, 1)), in2027, res)).isEqualTo("Sun, 1 Nov 2026")
    }

    @Test
    fun `chip for a range in one month`() {
        assertThat(DateLabels.chip(DateFilter.Range(d(2026, 11, 1), d(2026, 11, 3)), env, res))
            .isEqualTo("1 – 3 Nov 2026")
    }

    @Test
    fun `chip for a range across months`() {
        assertThat(DateLabels.chip(DateFilter.Range(d(2026, 10, 29), d(2026, 11, 3)), env, res))
            .isEqualTo("29 Oct – 3 Nov 2026")
    }

    @Test
    fun `chip for a range across years`() {
        assertThat(DateLabels.chip(DateFilter.Range(d(2025, 12, 29), d(2026, 1, 3)), env, res))
            .isEqualTo("29 Dec 2025 – 3 Jan 2026")
    }

    @Test
    fun `chip for a one day range reads as a single day`() {
        assertThat(DateLabels.chip(DateFilter.Range(d(2026, 11, 3), d(2026, 11, 3)), env, res))
            .isEqualTo("Tue, 3 Nov")
    }

    @Test
    fun `stamp and file sheet header`() {
        assertThat(DateLabels.stamp(londonMs("2026-11-03", "09:15"), env)).isEqualTo("Tue 3 Nov, 09:15")
        val file = item(ItemType.DOCUMENT, folder = "Download", sizeBytes = 240L * 1024)
        assertThat(DateLabels.fileSheetHeader(file, env)).isEqualTo("Download · 240 KB · Tue 3 Nov, 09:15")
        val bare = item(ItemType.DOCUMENT, folder = null, sizeBytes = null)
        assertThat(DateLabels.fileSheetHeader(bare, env)).isEqualTo("Tue 3 Nov, 09:15")
    }

    @Test
    fun `stamp honours the 12 hour setting`() {
        assertThat(DateLabels.stamp(londonMs("2026-11-03", "09:15"), testEnv(is24Hour = false)))
            .isEqualTo("Tue 3 Nov, 9:15 AM")
    }
}
