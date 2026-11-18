package com.kuriousdragon.smartindex.core.index.write

import com.kuriousdragon.smartindex.core.index.db.code
import com.kuriousdragon.smartindex.core.index.db.entity.ItemEntity
import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.core.model.ItemRecord
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.PhoneNumberNormalizer
import com.kuriousdragon.smartindex.core.model.Source
import javax.inject.Inject

/** Maps an [ItemRecord] to its stored row (LLD §4.7). */
class RecordMapper @Inject constructor(private val numbers: PhoneNumberNormalizer) {

    /** `number_norm` of [raw], computed once per distinct number through [normCache]. */
    fun norm(raw: String?, normCache: MutableMap<String, String?>): String? {
        if (raw == null) return null
        if (normCache.containsKey(raw)) return normCache[raw]
        return numbers.normalize(raw).also { normCache[raw] = it }
    }

    /** [normCache] = per-transaction numberRaw → number_norm cache (100k messages share ~2k numbers). */
    fun toEntity(
        r: ItemRecord,
        id: Long,
        contactName: String?,
        normCache: MutableMap<String, String?>,
    ): ItemEntity =
        ItemEntity(
            id = id,
            source = r.key.source.code(),
            sourceId = r.key.sourceId,
            type = r.type.code(),
            title = r.title,
            folder = r.folder,
            mime = r.mime,
            ext = r.ext,
            sizeBytes = r.sizeBytes,
            durationMs = r.durationMs,
            contentUri = r.contentUri,
            primaryTs = r.primaryTs,
            dateTaken = r.dates[DateKind.TAKEN],
            dateAdded = r.dates[DateKind.ADDED],
            dateModified = r.dates[DateKind.MODIFIED],
            dateEvent = r.dates[DateKind.EVENT],
            numberRaw = r.numberRaw,
            numberNorm = norm(r.numberRaw, normCache),
            contactName = if (r.key.source.hasContactName()) contactName else null,
            direction = r.direction?.code(),
            msgKind = r.msgKind?.code(),
            threadId = r.threadId,
            hasAttachment = r.hasAttachment,
            body = if (r.type == ItemType.MESSAGE) r.body else null,
            sourceVersion = r.sourceVersion,
        )

    companion object {
        /** Only telephony sources carry a denormalised contact name. */
        fun Source.hasContactName(): Boolean =
            this == Source.CALL_LOG || this == Source.SMS || this == Source.MMS
    }
}
