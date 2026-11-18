package com.kuriousdragon.smartindex.source.telephony.mms

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
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Locale
import javax.inject.Inject

/** Maps an [MmsRow] with its parts and participants to a MESSAGE [ItemRecord] (LLD §4.2, §4.3, §4.5). */
class MmsRowMapper @Inject constructor(private val display: NumberDisplay) {

    /** Null (and a counted skip) for rows without a date or with an unknown box. */
    fun map(row: MmsRow, content: MmsContent, participants: MmsParticipants, stats: RunStatsBuilder): ItemRecord? {
        val date = dateMs(row.dateRaw)
        if (date == null) {
            stats.skip("no_date")
            return null
        }
        val direction = direction(row.msgBox)
        if (direction == null) {
            stats.skip("unknown_type")
            stats.note("unknown_type_value_${row.msgBox}")
            return null
        }
        val numberRaw = participants.counterpart?.trim()?.takeIf {
            it.isNotEmpty() && it != MmsQueries.INSERT_ADDRESS_TOKEN &&
                it !in DefaultPhoneNumberNormalizer.PRIVATE_SENTINELS
        }
        val title = when {
            numberRaw != null -> display.format(numberRaw) +
                if (participants.othersCount > 0) " +${participants.othersCount}" else ""

            direction == Direction.RECEIVED -> TelephonyTitles.UNKNOWN_SENDER

            else -> TelephonyTitles.UNKNOWN_RECIPIENT
        }
        // Text is stored exactly (never trimmed inside) so highlight offsets match the original.
        val body = (listOfNotNull(subject(row)) + listOfNotNull(content.body))
            .joinToString("\n")
            .takeIf { it.isNotEmpty() }
            ?.take(TelephonyConfig.MAX_BODY_CHARS)
        val record = ItemRecord(
            key = SourceKey(Source.MMS, row.id),
            type = ItemType.MESSAGE,
            title = title,
            primaryTs = date,
            dates = mapOf(DateKind.EVENT to date),
            numberRaw = numberRaw,
            direction = direction,
            msgKind = MsgKind.MMS,
            threadId = row.threadId?.takeIf { it > 0 },
            hasAttachment = content.hasAttachment,
            body = body,
            sourceVersion = 0L,
            otherNumbers = participants.others,
        )
        return record.copy(sourceVersion = RecordHasher.hash(record))
    }

    /** MMS dates are seconds; a value that is already milliseconds (non-AOSP providers) is kept as is. */
    private fun dateMs(raw: Long?): Long? = when {
        raw == null || raw <= 0 -> null
        raw > MS_THRESHOLD -> raw
        else -> raw * MS_PER_SECOND
    }

    private fun direction(box: Int?): Direction? = when (box) {
        BOX_INBOX -> Direction.RECEIVED
        BOX_SENT, BOX_OUTBOX, BOX_FAILED -> Direction.SENT
        else -> null // includes drafts (excluded by the query) and 0 / null / OEM values
    }

    private fun subject(row: MmsRow): String? {
        val sub = row.subject?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (sub.lowercase(Locale.ROOT) in NO_SUBJECT) return null
        // AOSP stores encoded subjects as ISO-8859-1 strings; re-decode UTF-8 ones when that is lossless.
        if (row.subjectCharset == MmsQueries.CHARSET_UTF8 && sub.all { it.code <= MAX_LATIN1 }) {
            redecodeUtf8(sub)?.let { return it }
        }
        return sub
    }

    private fun redecodeUtf8(s: String): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(s.toByteArray(Charsets.ISO_8859_1)))
            .toString()
    } catch (e: CharacterCodingException) {
        null
    }

    private companion object {
        const val BOX_INBOX = 1
        const val BOX_SENT = 2
        const val BOX_OUTBOX = 4
        const val BOX_FAILED = 5
        const val MS_THRESHOLD = 100_000_000_000L
        const val MS_PER_SECOND = 1000L
        const val MAX_LATIN1 = 0xFF
        val NO_SUBJECT = setOf("no subject", "nosubject", "<no subject>", "(no subject)")
    }
}
