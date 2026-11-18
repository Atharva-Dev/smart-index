package com.kuriousdragon.smartindex.source.telephony.number

import android.content.Context
import android.telephony.TelephonyManager
import com.kuriousdragon.smartindex.core.model.CountryIsoSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Country used to read national-format numbers: SIM → network → device locale. Needs no permission. */
@Singleton
class TelephonyCountryIsoSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : CountryIsoSource {
    @Volatile private var iso: String? = null

    @Volatile private var loaded = false

    override fun countryIso(): String? {
        if (!loaded) refresh()
        return iso
    }

    /** Re-reads the country (called at the start of every run). */
    fun refresh() {
        iso = read()
        loaded = true
    }

    private fun read(): String? {
        val tm = context.getSystemService(TelephonyManager::class.java)
        val candidates = listOf(
            runCatching { tm?.simCountryIso }.getOrNull(),
            runCatching { tm?.networkCountryIso }.getOrNull(),
            Locale.getDefault().country,
        )
        return candidates.firstNotNullOfOrNull { c -> c?.trim()?.takeIf { it.length == 2 } }?.uppercase(Locale.ROOT)
    }
}
