package com.kuriousdragon.smartindex.tools.seeder.role

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.kuriousdragon.smartindex.tools.seeder.SeedLog

/**
 * Stores real incoming SMS in the inbox while the seeder holds the role and remembers the row id in
 * `keepSmsIds` so a wipe never deletes it (LLD §3.4). Message text is never logged.
 */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, messages[0].displayOriginatingAddress)
            put(Telephony.Sms.BODY, messages.joinToString("") { it.displayMessageBody.orEmpty() })
            put(Telephony.Sms.DATE, messages[0].timestampMillis)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            put(Telephony.Sms.READ, 0)
        }
        val uri = runCatching { context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) }.getOrNull()
        val id = uri?.lastPathSegment?.toLongOrNull()
        if (id != null) SeederPrefs(context).addKeepSmsId(id) else SeedLog.error("incoming-sms-store-failed")
    }
}
