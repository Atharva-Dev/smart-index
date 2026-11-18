package com.kuriousdragon.smartindex.feature.home.format

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DurationFormatTest {
    private val res = resources()

    @Test
    fun `call durations`() {
        assertThat(DurationFormat.call(0, res)).isEqualTo("0 s")
        assertThat(DurationFormat.call(38_000, res)).isEqualTo("38 s")
        assertThat(DurationFormat.call(60_000, res)).isEqualTo("1 min")
        assertThat(DurationFormat.call(252_000, res)).isEqualTo("4 min 12 s")
        assertThat(DurationFormat.call((62 * 60 + 5) * 1000L, res)).isEqualTo("62 min 5 s")
    }

    @Test
    fun `call durations drop the milliseconds`() {
        assertThat(DurationFormat.call(999, res)).isEqualTo("0 s")
        assertThat(DurationFormat.call(59_999, res)).isEqualTo("59 s")
    }

    @Test
    fun `media durations are m ss and h mm ss from one hour`() {
        assertThat(DurationFormat.media(0)).isEqualTo("0:00")
        assertThat(DurationFormat.media(5_000)).isEqualTo("0:05")
        assertThat(DurationFormat.media(252_000)).isEqualTo("4:12")
        assertThat(DurationFormat.media(59 * 60_000L + 59_000)).isEqualTo("59:59")
        assertThat(DurationFormat.media(3_600_000)).isEqualTo("1:00:00")
        assertThat(DurationFormat.media(3_725_000)).isEqualTo("1:02:05")
    }
}
