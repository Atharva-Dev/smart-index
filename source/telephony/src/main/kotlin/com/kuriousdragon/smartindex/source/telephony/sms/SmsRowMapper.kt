package com.kuriousdragon.smartindex.source.telephony.sms

import android.provider.Telephony.Sms
import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.core.model.DefaultPhoneNumberNormalizer
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.ItemRecord
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.MsgKind
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceKey
import com.kuriousdragon.smartindex.source.telephony.TelephonyConfig
import com.kuriousdragon.smartindex.source.telephony.common.NumberDisplay
import com.kuriousdragon.smartindex.source.telephony.common.RecordHasher
import com.kuriousdragon.smartindex.source.telephony.common.RunStatsBuilder
import com.kuriousdragon.smartindex.source.telephony.common.TelephonyTitles
import javax.inject.Inject

/** Maps an [SmsRow] to a MESSAGE [ItemRecord] (LLD — Telephony indexer §4.2, §4.3, §4.5). */
class SmsRowMapper @Inject constructor(private val display: NumberDisplay) {

    /** Null (and a counted skip) for rows without a date or with an unknown box. */
    fun map(row: SmsRow, stats: RunStatsBuilder): ItemRecord? {
        val date = row.dateMs?.takeIf { it > 0 }
        if (date == null) {
            stats.skip("no_date")
            return null
        }
        val direction = direction(row.type)
        if (direction == null) {
            stats.skip("unknown_type")
            stats.note("unknown_type_value_${row.type}")
            return null
        }
        val numberRaw = row.address?.trim()
            ?.takeIf { it.isNotEmpty() && it !in DefaultPhoneNumberNormalizer.PRIVATE_SENTINELS }
        val title = when {
            numberRaw != null -> display.format(numberRaw)
            direction == Direction.RECEIVED -> TelephonyTitles.UNKNOWN_SENDER
            else -> TelephonyTitles.UNKNOWN_RECIPIENT
        }
        // Stored exactly (never trimmed) so highlight offsets match the original text.
        val body = row.body?.takeIf { it.isNotBlank() }?.take(TelephonyConfig.MAX_BODY_CHARS)
        val record = ItemRecord(
            key = SourceKey(Source.SMS, row.id),
            type = ItemType.MESSAGE,
            title = title,
            primaryTs = date,
            dates = mapOf(DateKind.EVENT to date),
            numberRaw = numberRaw,
            direction = direction,
            msgKind = MsgKind.SMS,
            threadId = row.threadId?.takeIf { it > 0 },
            body = body,
            sourceVersion = 0L,
        )
        return record.copy(sourceVersion = RecordHasher.hash(record))
    }

    private fun direction(type: Int?): Direction? = when (type) {
        Sms.MESSAGE_TYPE_INBOX -> Direction.RECEIVED

        Sms.MESSAGE_TYPE_SENT, Sms.MESSAGE_TYPE_OUTBOX, Sms.MESSAGE_TYPE_FAILED, Sms.MESSAGE_TYPE_QUEUED ->
            Direction.SENT

        else -> null // includes drafts (excluded by the query) and 0 / null / OEM values
    }
}
