package com.kuriousdragon.smartindex.source.telephony.mms

import android.provider.Telephony.Mms
import com.kuriousdragon.smartindex.source.telephony.common.CursorReader
import com.kuriousdragon.smartindex.source.telephony.common.ProviderRow
import com.kuriousdragon.smartindex.source.telephony.common.RowReader

/** One MMS row as read from the provider; every field except [id] may be missing. [dateRaw] is in seconds. */
data class MmsRow(
    override val id: Long,
    val threadId: Long?,
    val dateRaw: Long?,
    val msgBox: Int?,
    val subject: String?,
    val subjectCharset: Int?,
) : ProviderRow

/** Text and attachment flag of one message, assembled from its parts (LLD §4.2 "MMS assembly"). */
data class MmsContent(val body: String?, val hasAttachment: Boolean) {
    companion object {
        val EMPTY = MmsContent(null, false)
    }
}

/**
 * Counterpart address and the further participants of the message (never including the counterpart). The
 * title suffix ` +<n>` uses [othersCount]; the numbers themselves are indexed for contact-name search only.
 */
data class MmsParticipants(val counterpart: String?, val others: List<String>) {
    val othersCount: Int get() = others.size

    companion object {
        val UNKNOWN = MmsParticipants(null, emptyList())
    }
}

/** Reads an [MmsRow]; null only when `_id` is null or ≤ 0. */
object MmsRowReader : RowReader<MmsRow> {
    override fun read(reader: CursorReader): MmsRow? {
        val id = reader.long(Mms._ID)?.takeIf { it > 0 } ?: return null
        return MmsRow(
            id = id,
            threadId = reader.long(Mms.THREAD_ID),
            dateRaw = reader.long(Mms.DATE),
            msgBox = reader.int(Mms.MESSAGE_BOX),
            subject = reader.string(Mms.SUBJECT),
            subjectCharset = reader.int(Mms.SUBJECT_CHARSET),
        )
    }
}
