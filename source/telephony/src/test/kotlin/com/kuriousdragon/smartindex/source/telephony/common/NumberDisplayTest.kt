package com.kuriousdragon.smartindex.source.telephony.common

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.CountryIsoSource
import com.kuriousdragon.smartindex.core.model.DefaultPhoneNumberNormalizer
import com.kuriousdragon.smartindex.core.model.PhoneNumberNormalizer
import com.kuriousdragon.smartindex.source.telephony.TelephonyConfig
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NumberDisplayTest {
    private val country = CountryIsoSource { "IN" }
    private val real = DefaultPhoneNumberNormalizer(country)
    private var normalizeCalls = 0
    private val counting = object : PhoneNumberNormalizer {
        override fun normalize(raw: String?): String? {
            normalizeCalls++
            return real.normalize(raw)
        }

        override fun digitsOnly(raw: String?): String? = real.digitsOnly(raw)
    }
    private val display = NumberDisplay(country, counting)

    @Test
    fun `phone numbers are formatted with the country`() {
        assertThat(display.format("+919876543210")).isEqualTo("+91 98765 43210")
        assertThat(display.format("  09876543210  ")).isEqualTo("098765 43210")
    }

    @Test
    fun `alphanumeric senders and short codes are returned trimmed`() {
        assertThat(display.format(" AX-HDFCBK ")).isEqualTo("AX-HDFCBK")
        assertThat(display.format(" 56161 ")).isEqualTo("56161")
        assertThat(display.format("*121#")).isEqualTo("*121#")
    }

    @Test
    fun `a phone number without a known country is returned trimmed`() {
        val none = CountryIsoSource { null }
        val noCountry = NumberDisplay(none, DefaultPhoneNumberNormalizer(none))
        assertThat(noCountry.format(" +919876543210 ")).isEqualTo("+919876543210")
    }

    @Test
    fun `results are cached until clear is called`() {
        display.format("+919876543210")
        val after = normalizeCalls
        display.format("+919876543210")
        assertThat(normalizeCalls).isEqualTo(after)
        display.clear()
        display.format("+919876543210")
        assertThat(normalizeCalls).isEqualTo(after + 1)
    }

    @Test
    fun `the cache is cleared when it grows past the maximum`() {
        val total = TelephonyConfig.DISPLAY_CACHE_MAX + 2
        for (i in 0 until total) display.format("code$i")
        val before = normalizeCalls
        display.format("code${total - 1}") // still cached
        assertThat(normalizeCalls).isEqualTo(before)
        display.format("code0") // dropped by the clear
        assertThat(normalizeCalls).isEqualTo(before + 1)
    }
}
