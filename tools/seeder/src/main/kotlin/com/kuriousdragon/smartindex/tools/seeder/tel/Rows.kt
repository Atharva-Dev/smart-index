package com.kuriousdragon.smartindex.tools.seeder.tel

import android.content.ContentValues
import android.provider.CallLog
import android.provider.Telephony
import com.kuriousdragon.smartindex.tools.seeder.gen.SeedCall
import com.kuriousdragon.smartindex.tools.seeder.gen.SeedMms
import com.kuriousdragon.smartindex.tools.seeder.gen.SeedSms

/** Pure ContentValues builders for the telephony providers (LLD §3.4, §4.2–4.3; shapes in Telephony LLD §4.1–4.3). */
object Rows {
    /** IANA MIBenum of UTF-8, used for every MMS charset column. */
    const val CHARSET_UTF8 = 106

    const val MMS_VERSION_1_2 = 18
    const val M_TYPE_RETRIEVE_CONF = 132
    const val M_TYPE_SEND_REQ = 128
    const val ADDR_FROM = 137
    const val ADDR_TO = 151
    const val INSERT_ADDRESS_TOKEN = "insert-address-token"
    const val MMS_CONTENT_TYPE = "application/vnd.wap.multipart.related"
    const val SMIL_TYPE = "application/smil"

    fun callValues(call: SeedCall): ContentValues = ContentValues().apply {
        put(CallLog.Calls.NUMBER, call.number.orEmpty())
        put(CallLog.Calls.DATE, call.startMs)
        put(CallLog.Calls.DURATION, call.durationSec)
        put(CallLog.Calls.TYPE, call.type)
        put(CallLog.Calls.NUMBER_PRESENTATION, call.presentation)
        put(CallLog.Calls.NEW, 0)
        put(CallLog.Calls.IS_READ, 1)
        put(CallLog.Calls.PHONE_ACCOUNT_ID, call.phoneAccountId)
    }

    fun smsValues(sms: SeedSms, threadId: Long): ContentValues = ContentValues().apply {
        put(Telephony.Sms.ADDRESS, sms.address)
        put(Telephony.Sms.BODY, sms.body)
        put(Telephony.Sms.DATE, sms.dateMs)
        put(Telephony.Sms.DATE_SENT, sms.dateMs)
        put(Telephony.Sms.TYPE, sms.type)
        put(Telephony.Sms.READ, 1)
        put(Telephony.Sms.SEEN, 1)
        put(Telephony.Sms.THREAD_ID, threadId)
    }

    /** The `content://mms` row; MMS dates are in seconds (unlike SMS). */
    fun mmsPduValues(mms: SeedMms, threadId: Long): ContentValues = ContentValues().apply {
        put(Telephony.Mms.THREAD_ID, threadId)
        put(Telephony.Mms.DATE, mms.dateMs / 1000)
        put(Telephony.Mms.DATE_SENT, mms.dateMs / 1000)
        put(Telephony.Mms.MESSAGE_BOX, mms.box)
        put(
            Telephony.Mms.MESSAGE_TYPE,
            if (mms.box ==
                Telephony.Mms.MESSAGE_BOX_SENT
            ) {
                M_TYPE_SEND_REQ
            } else {
                M_TYPE_RETRIEVE_CONF
            },
        )
        put(Telephony.Mms.READ, 1)
        put(Telephony.Mms.SEEN, 1)
        if (mms.subject != null) {
            put(Telephony.Mms.SUBJECT, mms.subject)
            put(Telephony.Mms.SUBJECT_CHARSET, CHARSET_UTF8)
        }
        put(Telephony.Mms.CONTENT_TYPE, MMS_CONTENT_TYPE)
        put(Telephony.Mms.MMS_VERSION, MMS_VERSION_1_2)
    }

    /** Minimal SMIL part (no layout): the platform and Samsung Messages only need its presence. */
    fun mmsSmilPart(msgId: Long): ContentValues = ContentValues().apply {
        put(Telephony.Mms.Part.MSG_ID, msgId)
        put(Telephony.Mms.Part.CONTENT_TYPE, SMIL_TYPE)
        put(Telephony.Mms.Part.CONTENT_ID, "<smil>")
        put(Telephony.Mms.Part.CONTENT_LOCATION, "smil.xml")
        put(Telephony.Mms.Part.NAME, "smil.xml")
        put(Telephony.Mms.Part.CHARSET, CHARSET_UTF8)
        put(Telephony.Mms.Part.TEXT, SMIL_BODY)
    }

    fun mmsTextPart(msgId: Long, index: Int, text: String): ContentValues = ContentValues().apply {
        put(Telephony.Mms.Part.MSG_ID, msgId)
        put(Telephony.Mms.Part.CONTENT_TYPE, "text/plain")
        put(Telephony.Mms.Part.CONTENT_ID, "<text_$index>")
        put(Telephony.Mms.Part.CONTENT_LOCATION, "text_$index.txt")
        put(Telephony.Mms.Part.NAME, "text_$index.txt")
        put(Telephony.Mms.Part.CHARSET, CHARSET_UTF8)
        put(Telephony.Mms.Part.TEXT, text)
    }

    /** Image part row; the JPEG bytes are written afterwards through `openOutputStream` on the part URI. */
    fun mmsImagePart(msgId: Long, index: Int): ContentValues = ContentValues().apply {
        put(Telephony.Mms.Part.MSG_ID, msgId)
        put(Telephony.Mms.Part.CONTENT_TYPE, "image/jpeg")
        put(Telephony.Mms.Part.CONTENT_ID, "<image_$index>")
        put(Telephony.Mms.Part.CONTENT_LOCATION, "image_$index.jpg")
        put(Telephony.Mms.Part.NAME, "image_$index.jpg")
    }

    fun mmsAddr(msgId: Long, address: String, type: Int): ContentValues = ContentValues().apply {
        put(Telephony.Mms.Addr.MSG_ID, msgId)
        put(Telephony.Mms.Addr.ADDRESS, address)
        put(Telephony.Mms.Addr.TYPE, type)
        put(Telephony.Mms.Addr.CHARSET, CHARSET_UTF8)
    }

    /**
     * Address rows of [mms]: received = FROM counterpart + TO the other group members; sent = FROM
     * [INSERT_ADDRESS_TOKEN] + TO every address.
     */
    fun mmsAddrRows(mms: SeedMms, msgId: Long): List<ContentValues> {
        val sent = mms.box == Telephony.Mms.MESSAGE_BOX_SENT
        val from = if (sent) INSERT_ADDRESS_TOKEN else mms.addresses.first()
        val to = if (sent) mms.addresses else mms.addresses.drop(1)
        return listOf(mmsAddr(msgId, from, ADDR_FROM)) + to.map { mmsAddr(msgId, it, ADDR_TO) }
    }

    private const val SMIL_BODY =
        "<smil><head><layout><root-layout width=\"320\" height=\"480\"/></layout></head><body><par dur=\"5000ms\"/></body></smil>"
}
