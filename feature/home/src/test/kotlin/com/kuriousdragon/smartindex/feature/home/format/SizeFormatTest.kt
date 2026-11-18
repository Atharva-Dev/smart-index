package com.kuriousdragon.smartindex.feature.home.format

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

class SizeFormatTest {
    private fun f(bytes: Long) = SizeFormat.format(bytes, Locale.UK)

    @Test
    fun `bytes have no decimals`() {
        assertThat(f(0)).isEqualTo("0 B")
        assertThat(f(239)).isEqualTo("239 B")
    }

    @Test
    fun `values from 100 have no decimals and smaller ones have one`() {
        assertThat(f(240L * 1024)).isEqualTo("240 KB")
        assertThat(f(1023L * 1024)).isEqualTo("1023 KB")
        assertThat(f((99.9 * 1024 * 1024).toLong())).isEqualTo("99.9 MB")
        assertThat(f(100L * 1024 * 1024)).isEqualTo("100 MB")
        assertThat(f((3.2 * 1024 * 1024).toLong())).isEqualTo("3.2 MB")
        assertThat(f((1.4 * 1024 * 1024 * 1024).toLong())).isEqualTo("1.4 GB")
    }

    @Test
    fun `a value that rounds up to the next unit moves to it`() {
        assertThat(f(1024L * 1024 - 1)).isEqualTo("1.0 MB")
        assertThat(f((99.96 * 1024 * 1024).toLong())).isEqualTo("100 MB")
    }

    @Test
    fun `terabytes are the largest unit`() {
        assertThat(f(5L * 1024 * 1024 * 1024 * 1024)).isEqualTo("5.0 TB")
        assertThat(f(2048L * 1024 * 1024 * 1024 * 1024)).isEqualTo("2048 TB")
    }

    @Test
    fun `the decimal separator follows the locale`() {
        assertThat(SizeFormat.format((1.5 * 1024 * 1024).toLong(), Locale.GERMANY)).isEqualTo("1,5 MB")
    }
}
