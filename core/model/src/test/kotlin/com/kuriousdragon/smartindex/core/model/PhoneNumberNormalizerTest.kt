package com.kuriousdragon.smartindex.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** LLD — Telephony indexer §7.2: every rule of §4.4 against a fake [E164Formatter] (plain JVM). */
class PhoneNumberNormalizerTest {
    private class FakeFormatter(private val result: (String, String) -> String?) : E164Formatter {
        val calls = mutableListOf<Pair<String, String>>()

        override fun formatE164(number: String, countryIso: String): String? {
            calls += number to countryIso
            return result(number, countryIso)
        }
    }

    private val failing = FakeFormatter { _, _ -> null }

    private fun normalizer(country: String? = "IN", formatter: E164Formatter = failing) =
        DefaultPhoneNumberNormalizer({ country }, formatter)

    @Test
    fun `null blank and each private sentinel normalise to null`() {
        val n = normalizer()
        for (raw in listOf(null, "", "   ", "-1", "-2", "-3", "-4", " -2 ")) {
            assertThat(n.normalize(raw)).isNull()
        }
    }

    @Test
    fun `letters give an upper-case key without spaces`() {
        val n = normalizer()
        assertThat(n.normalize("AX-HDFCBK")).isEqualTo("AX-HDFCBK")
        assertThat(n.normalize("vm-airtel")).isEqualTo("VM-AIRTEL")
        assertThat(n.normalize(" vm - airtel ")).isEqualTo("VM-AIRTEL")
    }

    @Test
    fun `service codes are kept as they are without spaces`() {
        val n = normalizer()
        assertThat(n.normalize("*121#")).isEqualTo("*121#")
        assertThat(n.normalize("*121 #")).isEqualTo("*121#")
    }

    @Test
    fun `fewer than 7 digits is a short code and the formatter is not called`() {
        val fake = FakeFormatter { _, _ -> "+should-not-be-used" }
        val n = normalizer(formatter = fake)
        assertThat(n.normalize("121")).isEqualTo("121")
        assertThat(n.normalize("56161")).isEqualTo("56161")
        assertThat(n.normalize("123456")).isEqualTo("123456")
        assertThat(fake.calls).isEmpty()
    }

    @Test
    fun `a value without any digit normalises to null`() {
        assertThat(normalizer().normalize("+ - ()")).isNull()
    }

    @Test
    fun `seven digits already go through the formatter`() {
        val fake = FakeFormatter { _, _ -> "+911234567" }
        assertThat(normalizer(formatter = fake).normalize("1234567")).isEqualTo("+911234567")
        assertThat(fake.calls).hasSize(1)
    }

    @Test
    fun `Devanagari digits are converted to ASCII before the formatter is called`() {
        val fake = FakeFormatter { _, _ -> "+919876543210" }
        val result = normalizer(formatter = fake).normalize("+91 ९८७६५ ४३२१०")
        assertThat(result).isEqualTo("+919876543210")
        assertThat(fake.calls).containsExactly("+91 98765 43210" to "IN")
    }

    @Test
    fun `formatter result is used when it starts with a plus`() {
        val fake = FakeFormatter { _, _ -> "+919876543210" }
        assertThat(normalizer(formatter = fake).normalize("098765 43210")).isEqualTo("+919876543210")
        assertThat(fake.calls).containsExactly("098765 43210" to "IN")
    }

    @Test
    fun `formatter result without a plus is ignored and the fallback is used`() {
        val fake = FakeFormatter { _, _ -> "919876543210" }
        assertThat(normalizer(formatter = fake).normalize("098765 43210")).isEqualTo("09876543210")
    }

    @Test
    fun `formatter returning null falls back`() {
        val n = normalizer(formatter = failing)
        assertThat(n.normalize("+91 98765 43210")).isEqualTo("+919876543210")
        assertThat(n.normalize("0091 98765 43210")).isEqualTo("+919876543210")
        assertThat(n.normalize("098765 43210")).isEqualTo("09876543210")
    }

    @Test
    fun `formatter throwing falls back`() {
        val throwing = FakeFormatter { _, _ -> error("boom") }
        val n = normalizer(formatter = throwing)
        assertThat(n.normalize("+91 98765 43210")).isEqualTo("+919876543210")
        assertThat(n.normalize("0091 98765 43210")).isEqualTo("+919876543210")
        assertThat(n.normalize("098765 43210")).isEqualTo("09876543210")
        assertThat(throwing.calls).hasSize(3)
    }

    @Test
    fun `country null blank or not 2 letters skips the formatter and falls back`() {
        for (country in listOf(null, "", "  ", "IND", "I")) {
            val fake = FakeFormatter { _, _ -> "+should-not-be-used" }
            val n = normalizer(country = country, formatter = fake)
            assertThat(n.normalize("+91 98765 43210")).isEqualTo("+919876543210")
            assertThat(n.normalize("098765 43210")).isEqualTo("09876543210")
            assertThat(fake.calls).isEmpty()
        }
    }

    @Test
    fun `country is trimmed and upper-cased before it reaches the formatter`() {
        val fake = FakeFormatter { _, _ -> "+919876543210" }
        normalizer(country = " in ", formatter = fake).normalize("9876543210")
        assertThat(fake.calls).containsExactly("9876543210" to "IN")
    }

    @Test
    fun `digitsOnly follows the 4_4 table`() {
        val n = normalizer()
        val table = mapOf(
            "+91 98765 43210" to "919876543210",
            "+91-98765-43210" to "919876543210",
            "098765 43210" to "09876543210",
            "9876543210" to "9876543210",
            "0091 98765 43210" to "00919876543210",
            "+1 650-253-0000" to "16502530000",
            "+91 ९८७६५ ४३२१०" to "919876543210",
            "121" to "121",
            "56161" to "56161",
            "*121#" to "121",
            "-2" to "2",
        )
        for ((raw, expected) in table) {
            assertThat(n.digitsOnly(raw)).isEqualTo(expected)
        }
    }

    @Test
    fun `digitsOnly is null for letters blank null and no digits`() {
        val n = normalizer()
        for (raw in listOf("AX-HDFCBK", "vm-airtel", "", "  ", null, "+ -")) {
            assertThat(n.digitsOnly(raw)).isNull()
        }
    }
}
