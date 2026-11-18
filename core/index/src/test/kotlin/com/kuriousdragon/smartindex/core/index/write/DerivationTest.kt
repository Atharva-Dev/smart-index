package com.kuriousdragon.smartindex.core.index.write

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.index.TestE164
import com.kuriousdragon.smartindex.core.index.db.code
import com.kuriousdragon.smartindex.core.index.db.entity.ItemEntity
import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.core.model.DefaultPhoneNumberNormalizer
import com.kuriousdragon.smartindex.core.model.ItemType
import org.junit.Test

class DerivationTest {
    private val derivation = Derivation(DefaultPhoneNumberNormalizer({ "IN" }, TestE164))

    private fun entity(
        type: ItemType,
        taken: Long? = null,
        added: Long? = null,
        modified: Long? = null,
        event: Long? = null,
        number: String? = null,
        body: String? = null,
    ) = ItemEntity(
        id = 9, source = 1, sourceId = 1, type = type.code(), title = "T", folder = "F", mime = null, ext = "x",
        sizeBytes = null, durationMs = null, contentUri = null, primaryTs = 777, dateTaken = taken, dateAdded = added,
        dateModified = modified, dateEvent = event, numberRaw = number, numberNorm = null, contactName = null,
        direction = null, msgKind = null, threadId = null, hasAttachment = false, body = body, sourceVersion = 1,
    )

    private fun kinds(e: ItemEntity) = derivation.dates(e).associate { it.kind to it.ts }

    @Test
    fun `photo and video use TAKEN when present`() {
        for (t in listOf(ItemType.PHOTO, ItemType.VIDEO)) {
            assertThat(kinds(entity(t, taken = 1, added = 2))).containsExactly(DateKind.TAKEN.code(), 1L)
        }
    }

    @Test
    fun `photo falls back to ADDED then to EVENT of primary_ts`() {
        assertThat(kinds(entity(ItemType.PHOTO, added = 2))).containsExactly(DateKind.ADDED.code(), 2L)
        assertThat(kinds(entity(ItemType.PHOTO))).containsExactly(DateKind.EVENT.code(), 777L)
    }

    @Test
    fun `document and other file get ADDED and MODIFIED`() {
        for (t in listOf(ItemType.DOCUMENT, ItemType.OTHER_FILE)) {
            assertThat(kinds(entity(t, added = 2, modified = 3)))
                .containsExactly(DateKind.ADDED.code(), 2L, DateKind.MODIFIED.code(), 3L)
            assertThat(kinds(entity(t, modified = 3))).containsExactly(DateKind.MODIFIED.code(), 3L)
            assertThat(kinds(entity(t))).containsExactly(DateKind.EVENT.code(), 777L)
        }
    }

    @Test
    fun `call and message use EVENT or fall back to primary_ts`() {
        for (t in listOf(ItemType.CALL, ItemType.MESSAGE)) {
            assertThat(kinds(entity(t, event = 4))).containsExactly(DateKind.EVENT.code(), 4L)
            assertThat(kinds(entity(t))).containsExactly(DateKind.EVENT.code(), 777L)
        }
    }

    @Test
    fun `recording uses EVENT else MODIFIED else primary_ts`() {
        assertThat(
            kinds(entity(ItemType.RECORDING, event = 4, modified = 3)),
        ).containsExactly(DateKind.EVENT.code(), 4L)
        assertThat(kinds(entity(ItemType.RECORDING, modified = 3))).containsExactly(DateKind.MODIFIED.code(), 3L)
        assertThat(kinds(entity(ItemType.RECORDING))).containsExactly(DateKind.EVENT.code(), 777L)
    }

    @Test
    fun `derive builds key, dates and short text for the item id`() {
        val d = derivation.derive(entity(ItemType.MESSAGE, event = 4, number = "+91 98765 43210", body = "Hi"))
        assertThat(d.key.id).isEqualTo(9)
        assertThat(d.key.ts).isEqualTo(777)
        assertThat(d.dates.single().itemId).isEqualTo(9)
        assertThat(d.shortText.skey).isEqualTo("t\u0001f\u0001x\u0001+91 98765 43210\u0001919876543210\u0001hi")
    }
}
