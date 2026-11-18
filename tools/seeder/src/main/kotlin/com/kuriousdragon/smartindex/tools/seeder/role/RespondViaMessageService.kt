package com.kuriousdragon.smartindex.tools.seeder.role

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Stub required for `ROLE_SMS`; the seeder never sends anything. */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf()
        return START_NOT_STICKY
    }
}
