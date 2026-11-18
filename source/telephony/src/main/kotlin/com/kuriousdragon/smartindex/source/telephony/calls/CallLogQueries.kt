package com.kuriousdragon.smartindex.source.telephony.calls

import android.provider.CallLog.Calls
import com.kuriousdragon.smartindex.source.telephony.common.QuerySpec
import com.kuriousdragon.smartindex.source.telephony.common.SourceQueries

/** Call-log queries (LLD — Telephony indexer §4.1). Voicemail rows (`type = 4`) are excluded (D4). */
object CallLogQueries : SourceQueries {
    private val PROJECTION = listOf(
        Calls._ID,
        Calls.NUMBER,
        Calls.DATE,
        Calls.DURATION,
        Calls.TYPE,
        Calls.NUMBER_PRESENTATION,
    )

    private const val ELIGIBLE = "${Calls.TYPE} <> ${Calls.VOICEMAIL_TYPE}"
    private const val SORT = "${Calls._ID} ASC"

    override fun full() = QuerySpec(Calls.CONTENT_URI, PROJECTION, ELIGIBLE, sortOrder = SORT)

    override fun newRows(afterId: Long) = QuerySpec(
        uri = Calls.CONTENT_URI,
        projection = PROJECTION,
        selection = "$ELIGIBLE AND ${Calls._ID} > ?",
        args = listOf(afterId.toString()),
        sortOrder = SORT,
    )

    override fun recent(sinceMs: Long, upToId: Long) = QuerySpec(
        uri = Calls.CONTENT_URI,
        projection = PROJECTION,
        selection = "$ELIGIBLE AND ${Calls.DATE} >= ? AND ${Calls._ID} <= ?",
        args = listOf(sinceMs.toString(), upToId.toString()),
        sortOrder = SORT,
    )

    override fun idScan() = QuerySpec(Calls.CONTENT_URI, listOf(Calls._ID), ELIGIBLE)
}
