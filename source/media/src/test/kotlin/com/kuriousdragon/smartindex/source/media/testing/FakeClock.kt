package com.kuriousdragon.smartindex.source.media.testing

import com.kuriousdragon.smartindex.source.media.run.MediaClock

/** Manually advanced clock. */
class FakeClock(var now: Long = 1_700_000_000_000L, var elapsed: Long = 0L) : MediaClock {
    override fun nowMs(): Long = now

    override fun elapsedMs(): Long = elapsed
}
