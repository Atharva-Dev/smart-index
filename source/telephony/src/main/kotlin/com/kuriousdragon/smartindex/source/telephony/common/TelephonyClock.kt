package com.kuriousdragon.smartindex.source.telephony.common

import android.os.SystemClock
import javax.inject.Inject

/** Time seam: wall clock for stored timestamps, monotonic clock for durations and freshness windows. */
interface TelephonyClock {
    fun nowMs(): Long

    fun elapsedMs(): Long
}

class SystemTelephonyClock @Inject constructor() : TelephonyClock {
    override fun nowMs() = System.currentTimeMillis()

    override fun elapsedMs() = SystemClock.elapsedRealtime()
}
