package com.kuriousdragon.smartindex.source.telephony.testing

import com.kuriousdragon.smartindex.source.telephony.common.TelephonyClock

/** Manually advanced clock; [elapsed] is the monotonic one. */
class FakeClock(var now: Long = 1_700_000_000_000L, var elapsed: Long = 0L) : TelephonyClock {
    override fun nowMs(): Long = now

    override fun elapsedMs(): Long = elapsed

    fun advance(ms: Long) {
        now += ms
        elapsed += ms
    }
}
