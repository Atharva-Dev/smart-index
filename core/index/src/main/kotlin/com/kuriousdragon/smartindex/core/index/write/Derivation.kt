package com.kuriousdragon.smartindex.core.index.write

import com.kuriousdragon.smartindex.core.index.db.code
import com.kuriousdragon.smartindex.core.index.db.entity.ItemDateEntity
import com.kuriousdragon.smartindex.core.index.db.entity.ItemEntity
import com.kuriousdragon.smartindex.core.index.db.entity.ItemKeyEntity
import com.kuriousdragon.smartindex.core.index.db.entity.ShortTextEntity
import com.kuriousdragon.smartindex.core.index.db.itemType
import com.kuriousdragon.smartindex.core.index.text.SkeyBuilder
import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.PhoneNumberNormalizer
import javax.inject.Inject

/** Derives `item_key`, `item_date` and `short_text` from an item row (LLD §4.7). */
class Derivation @Inject constructor(private val numbers: PhoneNumberNormalizer) {
    data class Derived(
        val key: ItemKeyEntity,
        val dates: List<ItemDateEntity>,
        val shortText: ShortTextEntity,
    )

    fun derive(e: ItemEntity, extraNames: List<String> = emptyList()): Derived =
        Derived(ItemKeyEntity(e.id, e.type, e.primaryTs), dates(e), shortText(e, extraNames))

    fun shortText(e: ItemEntity, extraNames: List<String> = emptyList()): ShortTextEntity =
        ShortTextEntity(
            id = e.id,
            ts = e.primaryTs,
            type = e.type,
            skey = SkeyBuilder.build(
                title = e.title,
                folder = e.folder,
                ext = e.ext,
                numberRaw = e.numberRaw,
                numberDigits = numbers.digitsOnly(e.numberRaw),
                contactName = e.contactName,
                body = e.body,
                extraNames = extraNames,
            ),
        )

    fun dates(e: ItemEntity): List<ItemDateEntity> {
        val found: List<Pair<DateKind, Long>> =
            when (itemType(e.type)) {
                ItemType.PHOTO, ItemType.VIDEO ->
                    listOfNotNull(
                        (e.dateTaken?.let { DateKind.TAKEN to it })
                            ?: e.dateAdded?.let { DateKind.ADDED to it },
                    )

                ItemType.DOCUMENT, ItemType.OTHER_FILE ->
                    listOfNotNull(
                        e.dateAdded?.let { DateKind.ADDED to it },
                        e.dateModified?.let { DateKind.MODIFIED to it },
                    )

                ItemType.CALL, ItemType.MESSAGE ->
                    listOfNotNull(e.dateEvent?.let { DateKind.EVENT to it })

                ItemType.RECORDING ->
                    listOfNotNull(
                        (e.dateEvent?.let { DateKind.EVENT to it })
                            ?: e.dateModified?.let { DateKind.MODIFIED to it },
                    )
            }
        val rows = found.ifEmpty { listOf(DateKind.EVENT to e.primaryTs) }
        return rows.map { (kind, ts) -> ItemDateEntity(e.id, kind.code(), e.type, ts) }
    }
}
