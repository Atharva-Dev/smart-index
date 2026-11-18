package com.kuriousdragon.smartindex.source.telephony.mms

import android.net.Uri
import android.provider.Telephony.Mms
import android.provider.Telephony.MmsSms
import android.provider.Telephony.ThreadsColumns
import com.kuriousdragon.smartindex.source.telephony.common.QuerySpec
import com.kuriousdragon.smartindex.source.telephony.common.SourceQueries

/**
 * MMS queries (LLD — Telephony indexer §4.1 "MMS"). Only real messages are eligible: drafts and
 * delivery / read reports (`m_type` 134 / 136) are excluded (D9).
 */
object MmsQueries : SourceQueries {
    // PDU header values (`PduHeaders` is hidden API, so they are named here).
    /** `m_type` of an outgoing message (`MESSAGE_TYPE_SEND_REQ`). */
    const val M_TYPE_SEND_REQ = 128

    /** `m_type` of a not-yet-downloaded incoming message (`MESSAGE_TYPE_NOTIFICATION_IND`). */
    const val M_TYPE_NOTIFICATION_IND = 130

    /** `m_type` of a downloaded incoming message (`MESSAGE_TYPE_RETRIEVE_CONF`). */
    const val M_TYPE_RETRIEVE_CONF = 132

    /** `addr.type` of the sender (`PduHeaders.FROM`). */
    const val ADDR_TYPE_FROM = 137

    /** `addr.type` of a recipient (`PduHeaders.TO`). */
    const val ADDR_TYPE_TO = 151

    /** AOSP placeholder stored in `addr` for "this device"; never a counterpart. */
    const val INSERT_ADDRESS_TOKEN = "insert-address-token"

    /** `sub_cs` value for UTF-8 (IANA MIBenum 106). */
    const val CHARSET_UTF8 = 106

    /** `Mms.Part.SEQUENCE_ORDER` (`seq`), not in the public SDK. */
    const val PART_SEQ = "seq"

    private val PROJECTION =
        listOf(Mms._ID, Mms.THREAD_ID, Mms.DATE, Mms.MESSAGE_BOX, Mms.SUBJECT, Mms.SUBJECT_CHARSET)

    private const val ELIGIBLE =
        "${Mms.MESSAGE_BOX} <> ${Mms.MESSAGE_BOX_DRAFTS} AND " +
            "${Mms.MESSAGE_TYPE} IN ($M_TYPE_SEND_REQ,$M_TYPE_NOTIFICATION_IND,$M_TYPE_RETRIEVE_CONF)"
    private const val SORT = "${Mms._ID} ASC"

    private val PART_URI: Uri = Uri.parse("content://mms/part")
    private val CANONICAL_URI: Uri = Uri.parse("content://mms-sms/canonical-addresses")

    /** `Mms.Part` column names used by the parts query (`_id` is the part id). */
    val PART_PROJECTION = listOf(
        Mms.Part._ID,
        Mms.Part.MSG_ID,
        PART_SEQ,
        Mms.Part.CONTENT_TYPE,
        Mms.Part.TEXT,
    )

    override fun full() = QuerySpec(Mms.CONTENT_URI, PROJECTION, ELIGIBLE, sortOrder = SORT)

    override fun newRows(afterId: Long) = QuerySpec(
        uri = Mms.CONTENT_URI,
        projection = PROJECTION,
        selection = "$ELIGIBLE AND ${Mms._ID} > ?",
        args = listOf(afterId.toString()),
        sortOrder = SORT,
    )

    /** `pdu.date` is in **seconds**, so [sinceMs] is divided by 1000. */
    override fun recent(sinceMs: Long, upToId: Long) = QuerySpec(
        uri = Mms.CONTENT_URI,
        projection = PROJECTION,
        selection = "$ELIGIBLE AND ${Mms.DATE} >= ? AND ${Mms._ID} <= ?",
        args = listOf((sinceMs / MS_PER_SECOND).toString(), upToId.toString()),
        sortOrder = SORT,
    )

    override fun idScan() = QuerySpec(Mms.CONTENT_URI, listOf(Mms._ID), ELIGIBLE)

    /**
     * All parts of the messages [ids] (≤ 500) in one query. The ids are inlined, not bound, to stay under
     * SQLite's 999-argument limit; they are numbers read from the provider, not user input.
     */
    fun parts(ids: LongArray) = QuerySpec(
        uri = PART_URI,
        projection = PART_PROJECTION,
        selection = "${Mms.Part.MSG_ID} IN (${ids.joinToString(",")})",
        sortOrder = "${Mms.Part.MSG_ID} ASC, ${PART_SEQ} ASC",
    )

    /** `content://mms/part/<partId>`, read with `openText` when a text part has no inline `text`. */
    fun partUri(partId: Long): Uri = Uri.withAppendedPath(PART_URI, partId.toString())

    /** Thread id → `recipient_ids` (once per run). */
    fun threads() = QuerySpec(
        uri = MmsSms.CONTENT_CONVERSATIONS_URI.buildUpon().appendQueryParameter("simple", "true").build(),
        projection = listOf(Mms._ID, ThreadsColumns.RECIPIENT_IDS),
    )

    /** Canonical address id → address (once per run). */
    fun canonicalAddresses() = QuerySpec(CANONICAL_URI, listOf(Mms._ID, Mms.Addr.ADDRESS))

    /** FROM / TO addresses of one message (`content://mms/<id>/addr`). */
    fun addr(messageId: Long) = QuerySpec(
        uri = Mms.CONTENT_URI.buildUpon().appendPath(messageId.toString()).appendPath("addr").build(),
        projection = listOf(Mms.Addr.ADDRESS, Mms.Addr.TYPE),
        selection = "${Mms.Addr.TYPE} IN ($ADDR_TYPE_FROM,$ADDR_TYPE_TO)",
        sortOrder = "${Mms.Addr._ID} ASC",
    )

    private const val MS_PER_SECOND = 1000L
}
