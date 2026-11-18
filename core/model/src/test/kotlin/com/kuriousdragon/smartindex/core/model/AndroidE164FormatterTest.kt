package com.kuriousdragon.smartindex.core.model

import android.os.Build
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** LLD — Telephony indexer §4.4 table through the real [AndroidE164Formatter] (libphonenumber in the framework). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidE164FormatterTest {
    private fun normalizer(country: String?) = DefaultPhoneNumberNormalizer({ country }, AndroidE164Formatter)

    private data class Row(val raw: String?, val normalized: String?, val digitsOnly: String?)

    private val rowsForIndia = listOf(
        Row("+91 98765 43210", "+919876543210", "919876543210"),
        Row("+91-98765-43210", "+919876543210", "919876543210"),
        Row("098765 43210", "+919876543210", "09876543210"),
        Row("9876543210", "+919876543210", "9876543210"),
        Row("0091 98765 43210", "+919876543210", "00919876543210"),
        Row("+1 650-253-0000", "+16502530000", "16502530000"),
        Row("+91 ९८७६५ ४३२१०", "+919876543210", "919876543210"),
        Row("121", "121", "121"),
        Row("56161", "56161", "56161"),
        Row("AX-HDFCBK", "AX-HDFCBK", null),
        Row("vm-airtel", "VM-AIRTEL", null),
        Row("*121#", "*121#", "121"),
        Row("", null, null),
        Row(" ", null, null),
        Row(null, null, null),
    )

    @Test
    fun `runs on the sdk the config asks for`() {
        assertThat(Build.VERSION.SDK_INT).isEqualTo(34)
    }

    @Test
    fun `table rows with country IN`() {
        val n = normalizer("IN")
        for (row in rowsForIndia) {
            assertThat(n.normalize(row.raw)).isEqualTo(row.normalized)
            assertThat(n.digitsOnly(row.raw)).isEqualTo(row.digitsOnly)
        }
    }

    @Test
    fun `private sentinel -2 normalises to null with country IN`() {
        assertThat(normalizer("IN").normalize("-2")).isNull()
    }

    @Test
    fun `table rows with no country use the fallback`() {
        val n = normalizer(null)
        assertThat(n.normalize("098765 43210")).isEqualTo("09876543210")
        assertThat(n.digitsOnly("098765 43210")).isEqualTo("09876543210")
        assertThat(n.normalize("+91 98765 43210")).isEqualTo("+919876543210")
        assertThat(n.digitsOnly("+91 98765 43210")).isEqualTo("919876543210")
    }
}
