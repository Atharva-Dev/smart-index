package com.kuriousdragon.smartindex.source.telephony.common

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.ItemRecord
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.MsgKind
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceKey
import org.junit.Test

class RecordHasherTest {
    private val base = ItemRecord(
        key = SourceKey(Source.SMS, 7),
        type = ItemType.MESSAGE,
        title = "+91 98765 43210",
        primaryTs = 1_700_000_000_000L,
        dates = mapOf(DateKind.EVENT to 1_700_000_000_000L),
        folder = "f",
        mime = "m",
        ext = "e",
        sizeBytes = 1,
        durationMs = 2,
        contentUri = "u",
        numberRaw = "+919876543210",
        direction = Direction.RECEIVED,
        msgKind = MsgKind.SMS,
        threadId = 3,
        hasAttachment = false,
        body = "होटल सी व्यू में बुकिंग",
        sourceVersion = 0,
    )

    @Test
    fun `the same record gives the same hash`() {
        assertThat(RecordHasher.hash(base)).isEqualTo(RecordHasher.hash(base.copy()))
    }

    @Test
    fun `changing any single field changes the hash`() {
        val changed = listOf(
            base.copy(key = SourceKey(Source.MMS, 7)),
            base.copy(key = SourceKey(Source.SMS, 8)),
            base.copy(type = ItemType.CALL),
            base.copy(title = "x"),
            base.copy(primaryTs = 1),
            base.copy(dates = mapOf(DateKind.EVENT to 1L)),
            base.copy(folder = null),
            base.copy(mime = null),
            base.copy(ext = null),
            base.copy(sizeBytes = 9),
            base.copy(durationMs = 9),
            base.copy(contentUri = null),
            base.copy(numberRaw = null),
            base.copy(direction = Direction.SENT),
            base.copy(msgKind = MsgKind.MMS),
            base.copy(threadId = 4),
            base.copy(hasAttachment = true),
            base.copy(body = "other"),
            base.copy(otherNumbers = listOf("+911111122222")),
            base.copy(otherNumbers = listOf("+911111122222", "+917777788888")),
            base.copy(otherNumbers = listOf("+917777788888", "+911111122222")),
        ).map { RecordHasher.hash(it) }
        assertThat(changed).containsNoDuplicates()
        assertThat(changed).doesNotContain(RecordHasher.hash(base))
    }

    @Test
    fun `null and an empty string hash differently`() {
        assertThat(RecordHasher.hash(base.copy(body = null))).isNotEqualTo(RecordHasher.hash(base.copy(body = "")))
    }

    @Test
    fun `the sourceVersion field is ignored`() {
        assertThat(RecordHasher.hash(base.copy(sourceVersion = 99))).isEqualTo(RecordHasher.hash(base))
    }

    @Test
    fun `the order of the dates map does not matter`() {
        val a = base.copy(dates = linkedMapOf(DateKind.EVENT to 1L, DateKind.ADDED to 2L))
        val b = base.copy(dates = linkedMapOf(DateKind.ADDED to 2L, DateKind.EVENT to 1L))
        assertThat(RecordHasher.hash(a)).isEqualTo(RecordHasher.hash(b))
    }

    @Test
    fun `the mapping version takes part in the hash`() {
        val withVersion = fnv1a(expectedText(MAPPING = RecordHasher.MAPPING_VERSION))
        val withOtherVersion = fnv1a(expectedText(MAPPING = RecordHasher.MAPPING_VERSION + 1))
        assertThat(RecordHasher.hash(base)).isEqualTo(withVersion)
        assertThat(withVersion).isNotEqualTo(withOtherVersion)
    }

    @Test
    fun `the hash is FNV-1a 64 over fields in declaration order each followed by NUL`() {
        assertThat(RecordHasher.hash(base)).isEqualTo(fnv1a(expectedText(RecordHasher.MAPPING_VERSION)))
        assertThat(fnv1a("")).isEqualTo(-3750763034362895579L) // the 64-bit offset basis
        assertThat(fnv1a("a")).isEqualTo(-0x509c23b379fe1374L) // known FNV-1a 64 test vector for "a"
    }

    @Test
    fun `other numbers are appended after the body joined by commas, only when non-empty`() {
        val withOthers = base.copy(otherNumbers = listOf("+911111122222", "+917777788888"))
        assertThat(RecordHasher.hash(withOthers))
            .isEqualTo(fnv1a(expectedText(RecordHasher.MAPPING_VERSION) + "+911111122222,+917777788888\u0000"))
        assertThat(RecordHasher.hash(base.copy(otherNumbers = emptyList())))
            .isEqualTo(fnv1a(expectedText(RecordHasher.MAPPING_VERSION)))
    }

    @Test
    fun `the mapping version is 2`() {
        assertThat(RecordHasher.MAPPING_VERSION).isEqualTo(2)
    }

    private fun expectedText(MAPPING: Int): String {
        val r = base
        val fields = listOf(
            MAPPING, r.key.source, r.key.sourceId, r.type, r.title, r.primaryTs, "EVENT=${r.primaryTs}",
            r.folder, r.mime, r.ext, r.sizeBytes, r.durationMs, r.contentUri, r.numberRaw, r.direction,
            r.msgKind, r.threadId, r.hasAttachment, r.body,
        )
        return fields.joinToString("") { (it?.toString() ?: "\u0001") + "\u0000" }
    }

    private fun fnv1a(text: String): Long {
        var h = -3750763034362895579L
        for (b in text.toByteArray(Charsets.UTF_8)) h = (h xor (b.toLong() and 0xff)) * 0x100000001b3L
        return h
    }
}
