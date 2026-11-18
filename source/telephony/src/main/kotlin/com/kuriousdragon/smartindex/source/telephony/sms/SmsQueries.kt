package com.kuriousdragon.smartindex.source.telephony.sms

import android.provider.Telephony.Sms
import com.kuriousdragon.smartindex.source.telephony.common.QuerySpec
import com.kuriousdragon.smartindex.source.telephony.common.SourceQueries

/** SMS queries over all boxes (LLD — Telephony indexer §4.1). Drafts (`type = 3`) are excluded. */
object SmsQueries : SourceQueries {
    private val PROJECTION = listOf(Sms._ID, Sms.THREAD_ID, Sms.ADDRESS, Sms.DATE, Sms.TYPE, Sms.BODY)

    private const val ELIGIBLE = "${Sms.TYPE} <> ${Sms.MESSAGE_TYPE_DRAFT}"
    private const val SORT = "${Sms._ID} ASC"

    override fun full() = QuerySpec(Sms.CONTENT_URI, PROJECTION, ELIGIBLE, sortOrder = SORT)

    override fun newRows(afterId: Long) = QuerySpec(
        uri = Sms.CONTENT_URI,
        projection = PROJECTION,
        selection = "$ELIGIBLE AND ${Sms._ID} > ?",
        args = listOf(afterId.toString()),
        sortOrder = SORT,
    )

    /** `sms.date` is in milliseconds. */
    override fun recent(sinceMs: Long, upToId: Long) = QuerySpec(
        uri = Sms.CONTENT_URI,
        projection = PROJECTION,
        selection = "$ELIGIBLE AND ${Sms.DATE} >= ? AND ${Sms._ID} <= ?",
        args = listOf(sinceMs.toString(), upToId.toString()),
        sortOrder = SORT,
    )

    override fun idScan() = QuerySpec(Sms.CONTENT_URI, listOf(Sms._ID), ELIGIBLE)
}
