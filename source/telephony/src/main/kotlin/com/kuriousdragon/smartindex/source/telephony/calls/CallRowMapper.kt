package com.kuriousdragon.smartindex.source.telephony.calls

import android.provider.CallLog.Calls
import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.core.model.DefaultPhoneNumberNormalizer
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.ItemRecord
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceKey
import com.kuriousdragon.smartindex.source.telephony.common.NumberDisplay
import com.kuriousdragon.smartindex.source.telephony.common.RecordHasher
import com.kuriousdragon.smartindex.source.telephony.common.RunStatsBuilder
import com.kuriousdragon.smartindex.source.telephony.common.TelephonyTitles
import javax.inject.Inject

/** Maps a [CallRow] to a CALL [ItemRecord] (LLD — Telephony indexer §4.2, §4.3, §4.5). */
class CallRowMapper @Inject constructor(private val display: NumberDisplay) {

    /** Null (and a counted skip) for rows without a date or with an unknown call type. */
    fun map(row: CallRow, stats: RunStatsBuilder): ItemRecord? {
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
        // A missing presentation column counts as "allowed".
        val presentation = row.presentation ?: Calls.PRESENTATION_ALLOWED
        val numberRaw = if (presentation == Calls.PRESENTATION_ALLOWED) {
            row.number?.trim()?.takeIf { it.isNotEmpty() && it !in DefaultPhoneNumberNormalizer.PRIVATE_SENTINELS }
        } else {
            null
        }
        val title = if (numberRaw != null) display.format(numberRaw) else label(presentation)
        val record = ItemRecord(
            key = SourceKey(Source.CALL_LOG, row.id),
            type = ItemType.CALL,
            title = title,
            primaryTs = date,
            dates = mapOf(DateKind.EVENT to date),
            durationMs = maxOf(row.durationSec ?: 0L, 0L) * 1000L,
            numberRaw = numberRaw,
            direction = direction,
            sourceVersion = 0L,
        )
        return record.copy(sourceVersion = RecordHasher.hash(record))
    }

    private fun direction(type: Int?): Direction? = when (type) {
        Calls.INCOMING_TYPE, Calls.ANSWERED_EXTERNALLY_TYPE -> Direction.INCOMING
        Calls.OUTGOING_TYPE -> Direction.OUTGOING
        Calls.MISSED_TYPE -> Direction.MISSED
        Calls.REJECTED_TYPE -> Direction.REJECTED
        Calls.BLOCKED_TYPE -> Direction.BLOCKED
        else -> null // includes voicemail (excluded by the query) and OEM-specific values
    }

    private fun label(presentation: Int): String = when (presentation) {
        Calls.PRESENTATION_RESTRICTED -> TelephonyTitles.PRIVATE_NUMBER
        Calls.PRESENTATION_PAYPHONE -> TelephonyTitles.PAYPHONE
        else -> TelephonyTitles.UNKNOWN_NUMBER
    }
}
