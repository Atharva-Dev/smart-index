package com.kuriousdragon.smartindex.source.media.run

import android.os.SystemClock
import javax.inject.Inject

/** Wall and monotonic time seam (LLD — Media & file indexer §3.3.3). */
interface MediaClock {
    fun nowMs(): Long

    fun elapsedMs(): Long
}

class SystemMediaClock @Inject constructor() : MediaClock {
    override fun nowMs() = System.currentTimeMillis()

    override fun elapsedMs() = SystemClock.elapsedRealtime()
}
