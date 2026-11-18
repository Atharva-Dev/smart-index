package com.kuriousdragon.smartindex.core.model

import android.telephony.PhoneNumberUtils
import java.util.Locale

/**
 * Supplies the ISO 3166-1 alpha-2 country (e.g. "IN") used to read national-format numbers.
 * Returns null when unknown. Implemented by `TelephonyCountryIsoSource` (SIM → network → locale).
 */
fun interface CountryIsoSource {
    fun countryIso(): String?
}

/** Formats [number] to E.164 for [countryIso] (upper-case), or returns null when it can't be parsed. */
fun interface E164Formatter {
    fun formatE164(number: String, countryIso: String): String?
}

/** Framework implementation (libphonenumber inside android.telephony). */
object AndroidE164Formatter : E164Formatter {
    override fun formatE164(number: String, countryIso: String): String? =
        PhoneNumberUtils.formatNumberToE164(number, countryIso)
}

/**
 * The single number-normalisation algorithm of Smart Index (LLD — Telephony indexer §4.4).
 *
 * - `normalize` gives the join key used for `item.number_norm` and the `contact_name` table:
 *   E.164 ("+919876543210") for phone numbers, digits for short codes ("56161"),
 *   upper-cased text without spaces for alphanumeric senders ("AX-HDFCBK"),
 *   the code itself for service codes ("*121#"), null for blank/private/unknown.
 * - `digitsOnly` gives the digits-only search form indexed next to the raw number
 *   ("+91 98765 43210" → "919876543210"); null when the value has letters or no digits.
 *
 * Both are pure for a given country, thread-safe and never throw.
 */
interface PhoneNumberNormalizer {
    fun normalize(raw: String?): String?
    fun digitsOnly(raw: String?): String?
}

class DefaultPhoneNumberNormalizer(
    private val country: CountryIsoSource,
    private val e164: E164Formatter = AndroidE164Formatter,
) : PhoneNumberNormalizer {

    override fun normalize(raw: String?): String? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty() || t in PRIVATE_SENTINELS) return null
        if (t.any { it.isLetter() }) return t.filterNot { it.isWhitespace() }.uppercase(Locale.ROOT)
        val ascii = toAsciiDigits(t)
        if ('*' in ascii || '#' in ascii) return ascii.filterNot { it.isWhitespace() }
        val digits = ascii.filter { it in '0'..'9' }
        if (digits.isEmpty()) return null
        if (digits.length < MIN_SUBSCRIBER_DIGITS) return digits
        val iso = country.countryIso()?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.length == 2 }
        if (iso != null) {
            val formatted = runCatching { e164.formatE164(ascii, iso) }.getOrNull()
            if (formatted != null && formatted.length > 1 && formatted.startsWith("+")) return formatted
        }
        val leadsWithPlus = ascii.firstOrNull { it == '+' || it in '0'..'9' } == '+'
        return when {
            leadsWithPlus -> "+$digits"
            digits.startsWith("00") -> "+" + digits.drop(2)
            else -> digits
        }
    }

    override fun digitsOnly(raw: String?): String? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty() || t.any { it.isLetter() }) return null
        return toAsciiDigits(t).filter { it in '0'..'9' }.ifEmpty { null }
    }

    companion object {
        /** Fewer digits than this = short code (e.g. "121", "56161"); never prefixed with a country code. */
        const val MIN_SUBSCRIBER_DIGITS = 7

        /** Legacy call-log sentinels for private / unknown / payphone numbers. */
        val PRIVATE_SENTINELS: Set<String> = setOf("-1", "-2", "-3", "-4")

        private fun toAsciiDigits(s: String): String = buildString(s.length) {
            for (c in s) append(if (c.isDigit()) '0' + Character.digit(c, 10) else c)
        }
    }
}
