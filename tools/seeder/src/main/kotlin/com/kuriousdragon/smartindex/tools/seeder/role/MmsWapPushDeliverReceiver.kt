package com.kuriousdragon.smartindex.tools.seeder.role

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.kuriousdragon.smartindex.tools.seeder.SeedLog

/** Incoming MMS during seeding are dropped (the test phone is dedicated); documented in LLD §3.4. */
class MmsWapPushDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        SeedLog.warn("mms-dropped")
    }
}
