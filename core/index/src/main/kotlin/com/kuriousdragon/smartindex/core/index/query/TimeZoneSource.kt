package com.kuriousdragon.smartindex.core.index.query

import java.time.ZoneId
import javax.inject.Inject

/** The device time zone, read on every query so a zone change takes effect without re-indexing (LLD §5.5). */
fun interface TimeZoneSource {
    fun zone(): ZoneId
}

class SystemTimeZoneSource @Inject constructor() : TimeZoneSource {
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
