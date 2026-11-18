package com.kuriousdragon.smartindex.sync

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Wall clock, replaceable in tests. */
fun interface SyncClock {
    fun now(): Long
}

/** Free space of the app's storage, replaceable in tests. */
fun interface FreeSpace {
    fun availableBytes(): Long
}

/** [SyncClock] backed by [System.currentTimeMillis]. */
class SystemSyncClock @Inject constructor() : SyncClock {
    override fun now(): Long = System.currentTimeMillis()
}

/** [FreeSpace] backed by [StatFs] on the app's files directory. */
class AndroidFreeSpace @Inject constructor(
    @ApplicationContext private val context: Context,
) : FreeSpace {
    override fun availableBytes(): Long = StatFs(context.filesDir.path).availableBytes
}
