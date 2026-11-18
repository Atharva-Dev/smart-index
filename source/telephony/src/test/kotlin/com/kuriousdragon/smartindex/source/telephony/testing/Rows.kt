package com.kuriousdragon.smartindex.source.telephony.testing

import android.content.ContentValues
import android.provider.ContactsContract.CommonDataKinds.Phone

private fun values(vararg pairs: Pair<String, Any?>) = ContentValues().apply {
    for ((key, value) in pairs) {
        when (value) {
            null -> putNull(key)
            is Long -> put(key, value)
            is Int -> put(key, value)
            is String -> put(key, value)
            else -> error("unsupported value type for $key")
        }
    }
}

/** Row builders; column names are the real provider names. */
fun FakeTelephonyProviders.addCall(
    id: Long,
    number: String? = "+919876543210",
    date: Long? = 1_700_000_000_000L,
    duration: Long? = 30L,
    type: Int? = 1,
    presentation: Int? = 1,
) = insert(
    "calls",
    values(
        "_id" to id,
        "number" to number,
        "date" to date,
        "duration" to duration,
        "type" to type,
        "presentation" to presentation,
    ),
)

fun FakeTelephonyProviders.addSms(
    id: Long,
    threadId: Long? = 1L,
    address: String? = "+919876543210",
    date: Long? = 1_700_000_000_000L,
    type: Int? = 1,
    body: String? = "hello",
) = insert(
    "sms",
    values("_id" to id, "thread_id" to threadId, "address" to address, "date" to date, "type" to type, "body" to body),
)

fun FakeTelephonyProviders.addPdu(
    id: Long,
    threadId: Long? = 1L,
    date: Long? = 1_700_000_000L,
    msgBox: Int? = 1,
    subject: String? = null,
    subjectCharset: Int? = null,
    mType: Int? = 132,
) = insert(
    "pdu",
    values(
        "_id" to id,
        "thread_id" to threadId,
        "date" to date,
        "msg_box" to msgBox,
        "sub" to subject,
        "sub_cs" to subjectCharset,
        "m_type" to mType,
    ),
)

fun FakeTelephonyProviders.addPart(
    id: Long,
    messageId: Long,
    seq: Int = 0,
    contentType: String? = "text/plain",
    text: String? = null,
    data: String? = null,
) = insert(
    "part",
    values("_id" to id, "mid" to messageId, "seq" to seq, "ct" to contentType, "text" to text, "_data" to data),
)

fun FakeTelephonyProviders.addAddr(id: Long, messageId: Long, address: String?, type: Int) = insert(
    "addr",
    values("_id" to id, "msg_id" to messageId, "address" to address, "type" to type),
)

fun FakeTelephonyProviders.addThread(id: Long, recipientIds: String) =
    insert("threads", values("_id" to id, "recipient_ids" to recipientIds))

fun FakeTelephonyProviders.addCanonical(id: Long, address: String) =
    insert("canonical_addresses", values("_id" to id, "address" to address))

fun FakeTelephonyProviders.addPhone(
    contactId: Long,
    name: String?,
    number: String?,
    normalized: String? = null,
    superPrimary: Int = 0,
    starred: Int = 0,
) = insert(
    "phones",
    values(
        Phone.CONTACT_ID to contactId,
        Phone.DISPLAY_NAME to name,
        Phone.NUMBER to number,
        Phone.NORMALIZED_NUMBER to normalized,
        Phone.IS_SUPER_PRIMARY to superPrimary,
        "starred" to starred,
    ),
)
