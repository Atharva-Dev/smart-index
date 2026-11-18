package com.kuriousdragon.smartindex.source.telephony.calls

import android.provider.CallLog.Calls
import com.kuriousdragon.smartindex.source.telephony.common.CursorReader
import com.kuriousdragon.smartindex.source.telephony.common.ProviderRow
import com.kuriousdragon.smartindex.source.telephony.common.RowReader

/** One call-log row as read from the provider; every field except [id] may be missing. */
data class CallRow(
    override val id: Long,
    val number: String?,
    val dateMs: Long?,
    val durationSec: Long?,
    val type: Int?,
    val presentation: Int?,
) : ProviderRow

/** Reads a [CallRow]; null only when `_id` is null or ≤ 0. */
object CallRowReader : RowReader<CallRow> {
    override fun read(reader: CursorReader): CallRow? {
        val id = reader.long(Calls._ID)?.takeIf { it > 0 } ?: return null
        return CallRow(
            id = id,
            number = reader.string(Calls.NUMBER),
            dateMs = reader.long(Calls.DATE),
            durationSec = reader.long(Calls.DURATION),
            type = reader.int(Calls.TYPE),
            presentation = reader.int(Calls.NUMBER_PRESENTATION),
        )
    }
}
