package com.kuriousdragon.smartindex

import android.os.StrictMode

/** Debug-only StrictMode policies (LLD — App shell §6.4). Log only, never `penaltyDeath`. */
internal object StrictModeSetup {
    fun install() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .detectActivityLeaks()
                .detectFileUriExposure()
                .detectContentUriWithoutPermission()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build(),
        )
    }
}
