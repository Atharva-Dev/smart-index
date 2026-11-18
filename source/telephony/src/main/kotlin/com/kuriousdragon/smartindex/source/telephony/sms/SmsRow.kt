package com.kuriousdragon.smartindex.source.telephony.sms

import android.provider.Telephony.Sms
import com.kuriousdragon.smartindex.source.telephony.common.CursorReader
import com.kuriousdragon.smartindex.source.telephony.common.ProviderRow
import com.kuriousdragon.smartindex.source.telephony.common.RowReader

/** One SMS row as read from the provider; every field except [id] may be missing. */
data class SmsRow(
    override val id: Long,
    val threadId: Long?,
    val address: String?,
    val dateMs: Long?,
    val type: Int?,
    val body: String?,
) : ProviderRow

/** Reads an [SmsRow]; null only when `_id` is null or ≤ 0. */
object SmsRowReader : RowReader<SmsRow> {
    override fun read(reader: CursorReader): SmsRow? {
        val id = reader.long(Sms._ID)?.takeIf { it > 0 } ?: return null
        return SmsRow(
            id = id,
            threadId = reader.long(Sms.THREAD_ID),
            address = reader.string(Sms.ADDRESS),
            dateMs = reader.long(Sms.DATE),
            type = reader.int(Sms.TYPE),
            body = reader.string(Sms.BODY),
        )
    }
}
