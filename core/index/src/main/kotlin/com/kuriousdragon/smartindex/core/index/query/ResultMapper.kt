package com.kuriousdragon.smartindex.core.index.query

import com.kuriousdragon.smartindex.core.index.db.dao.ResultRow
import com.kuriousdragon.smartindex.core.index.db.direction
import com.kuriousdragon.smartindex.core.index.db.itemType
import com.kuriousdragon.smartindex.core.index.db.msgKind
import com.kuriousdragon.smartindex.core.index.text.Highlighter
import com.kuriousdragon.smartindex.core.model.Field
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.ResultItem

/** Turns a hydrated row into a [ResultItem] (LLD §5.8). */
object ResultMapper {
    /**
     * @param ts the time the key query placed the row at (timeline and range rules, see [ResultItem.time]).
     * @param foldedQuery the folded query, or `null` for a timeline / blank-text browse (no matches).
     */
    fun map(row: ResultRow, ts: Long, foldedQuery: String?): ResultItem {
        val type = itemType(row.type)
        val recording = type == ItemType.RECORDING
        val number = if (recording) row.callNumberRaw else row.numberRaw
        val contactName = if (recording) row.callContactName else row.contactName
        val snippet = if (type == ItemType.MESSAGE) Highlighter.snippet(row.body, foldedQuery) else null
        val matches =
            if (foldedQuery == null) {
                emptyMap()
            } else {
                buildMap {
                    put(Field.TITLE, Highlighter.ranges(row.title, foldedQuery))
                    put(Field.FOLDER, Highlighter.ranges(row.folder, foldedQuery))
                    put(Field.EXT, Highlighter.ranges(row.ext, foldedQuery))
                    put(Field.NUMBER, Highlighter.numberRanges(number, foldedQuery))
                    put(Field.CONTACT_NAME, Highlighter.ranges(contactName, foldedQuery))
                    put(Field.SNIPPET, snippet?.ranges.orEmpty())
                }.filterValues { it.isNotEmpty() }
            }
        return ResultItem(
            id = row.id,
            type = type,
            title = row.title,
            time = ts,
            folder = row.folder,
            sizeBytes = row.sizeBytes,
            durationMs = row.durationMs,
            ext = row.ext,
            direction = row.direction?.let(::direction),
            msgKind = row.msgKind?.let(::msgKind),
            hasAttachment = row.hasAttachment,
            contactName = contactName,
            number = number,
            contentUri = row.contentUri,
            snippet = snippet?.text,
            matches = matches,
            linkedRecordingId = row.linkedRecordingId,
        )
    }
}
