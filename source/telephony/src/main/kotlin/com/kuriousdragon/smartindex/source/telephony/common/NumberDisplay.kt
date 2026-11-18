package com.kuriousdragon.smartindex.source.telephony.common

import android.telephony.PhoneNumberUtils
import com.kuriousdragon.smartindex.core.model.CountryIsoSource
import com.kuriousdragon.smartindex.core.model.PhoneNumberNormalizer
import com.kuriousdragon.smartindex.source.telephony.TelephonyConfig
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Formatted number for titles (cached; titles never use the contact name, LLD D10). */
@Singleton
class NumberDisplay @Inject constructor(
    private val country: CountryIsoSource,
    private val normalizer: PhoneNumberNormalizer,
) {
    private val cache = ConcurrentHashMap<String, String>()

    /**
     * Phone numbers (`normalize()` starts with '+' or is all digits with ≥ 7 digits) →
     * `PhoneNumberUtils.formatNumber(raw.trim(), iso) ?: raw.trim()`. Everything else → `raw.trim()`.
     */
    fun format(raw: String): String {
        cache[raw]?.let { return it }
        val formatted = compute(raw)
        if (cache.size > TelephonyConfig.DISPLAY_CACHE_MAX) cache.clear()
        cache[raw] = formatted
        return formatted
    }

    /** Called at run start; the cache is also cleared when it grows past `DISPLAY_CACHE_MAX`. */
    fun clear() {
        cache.clear()
    }

    private fun compute(raw: String): String {
        val trimmed = raw.trim()
        val norm = normalizer.normalize(trimmed)
        val isPhone = norm != null &&
            (norm.startsWith("+") || (norm.all { it in '0'..'9' } && norm.length >= MIN_PHONE_DIGITS))
        if (!isPhone) return trimmed
        val iso = country.countryIso() ?: return trimmed
        return runCatching { PhoneNumberUtils.formatNumber(trimmed, iso) }.getOrNull() ?: trimmed
    }

    private companion object {
        const val MIN_PHONE_DIGITS = 7
    }
}
