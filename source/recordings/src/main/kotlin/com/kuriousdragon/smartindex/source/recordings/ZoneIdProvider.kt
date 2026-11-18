package com.kuriousdragon.smartindex.source.recordings

import java.time.ZoneId

/** Supplies the device time zone used to read the wall-clock time in Samsung file names. */
fun interface ZoneIdProvider {
    fun zone(): ZoneId
}
