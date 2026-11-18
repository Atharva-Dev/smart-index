package com.kuriousdragon.smartindex.tools.seeder.tel

import android.content.ContentResolver
import android.content.ContentValues
import android.provider.CallLog
import android.provider.Telephony
import com.kuriousdragon.smartindex.tools.seeder.FreshType
import com.kuriousdragon.smartindex.tools.seeder.SeedLog
import com.kuriousdragon.smartindex.tools.seeder.gen.PHONE_ACCOUNT_ID
import com.kuriousdragon.smartindex.tools.seeder.gen.SeedCall
import com.kuriousdragon.smartindex.tools.seeder.gen.SeedMms
import com.kuriousdragon.smartindex.tools.seeder.gen.SeedSms
import com.kuriousdragon.smartindex.tools.seeder.media.FreshFiles
import com.kuriousdragon.smartindex.tools.seeder.media.MediaStoreWriter
import com.kuriousdragon.smartindex.tools.seeder.wipe.Ledger
import java.time.ZoneId

/**
 * `cmd=fresh` (NFR-005); file types go through [MediaStoreWriter]. SMS, MMS and CALL: one row dated now whose text carries the token. Logs
 * `fresh type=<T> token=<tok> createdAtMs=<ms>`. A call has no free-text column, so its token goes into the
 * cached contact name (`CACHED_NAME`), which the indexer reads as the displayed name.
 */
class FreshWriter(
    private val resolver: ContentResolver,
    private val threads: ThreadResolver,
    private val media: MediaStoreWriter? = null,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Returns false (and logs an error) when the row could not be written. */
    suspend fun write(type: FreshType, token: String, ledger: Ledger): Boolean {
        val now = clock()
        val ok = try {
            when (type) {
                FreshType.SMS -> writeSms(token, now, ledger)
                FreshType.MMS -> writeMms(token, now, ledger)
                FreshType.CALL -> writeCall(token, now, ledger)
                else -> writeFile(type, token, now)
            }
        } catch (e: SecurityException) {
            SeedLog.error("fresh SecurityException")
            false
        }
        if (ok) {
            SeedLog.line("fresh type=${type.name} token=$token createdAtMs=$now")
        } else {
            SeedLog.error("fresh write-failed ${type.name}")
        }
        return ok
    }

    private suspend fun writeFile(type: FreshType, token: String, now: Long): Boolean {
        val writer = media ?: return false
        val file = FreshFiles.plan(type, token, now, zone) ?: return false
        val uri = writer.write(file)
        writer.verifyPending()
        return uri != null
    }

    private fun writeSms(token: String, now: Long, ledger: Ledger): Boolean {
        val sms = SeedSms(0, ADDRESS, "fresh message $token", Telephony.Sms.MESSAGE_TYPE_INBOX, now, ADDRESS)
        val uri = resolver.insert(Telephony.Sms.CONTENT_URI, Rows.smsValues(sms, threads.threadId(setOf(ADDRESS))))
        uri?.let { ledger.append("sms", it.toString()) }
        return uri != null
    }

    private fun writeMms(token: String, now: Long, ledger: Ledger): Boolean {
        val mms =
            SeedMms(0, Telephony.Mms.MESSAGE_BOX_INBOX, now, listOf(ADDRESS), null, listOf("fresh picture $token"), 0)
        val pdu = resolver.insert(Telephony.Mms.CONTENT_URI, Rows.mmsPduValues(mms, threads.threadId(setOf(ADDRESS))))
            ?: return false
        ledger.append("mms", pdu.toString())
        val id = pdu.lastPathSegment?.toLongOrNull() ?: return false
        val part = android.net.Uri.parse("content://mms/$id/part")
        val addr = android.net.Uri.parse("content://mms/$id/addr")
        var ok = resolver.insert(part, Rows.mmsSmilPart(id)) != null
        ok = (resolver.insert(part, Rows.mmsTextPart(id, 0, mms.texts[0])) != null) && ok
        for (row: ContentValues in Rows.mmsAddrRows(mms, id)) ok = (resolver.insert(addr, row) != null) && ok
        return ok
    }

    private fun writeCall(token: String, now: Long, ledger: Ledger): Boolean {
        val call = SeedCall(0, ADDRESS, CallLog.Calls.PRESENTATION_ALLOWED, CallLog.Calls.INCOMING_TYPE, now, 30)
        val values = Rows.callValues(call).apply { put(CallLog.Calls.CACHED_NAME, "Fresh $token") }
        check(call.phoneAccountId == PHONE_ACCOUNT_ID)
        val uri = resolver.insert(CallLog.Calls.CONTENT_URI, values)
        uri?.let { ledger.append("call", it.toString()) }
        return uri != null
    }

    private companion object {
        const val ADDRESS = "+919800000099"
    }
}
