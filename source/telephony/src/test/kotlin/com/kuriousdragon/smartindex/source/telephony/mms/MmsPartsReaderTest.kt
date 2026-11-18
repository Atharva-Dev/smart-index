package com.kuriousdragon.smartindex.source.telephony.mms

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.source.telephony.TelephonyConfig
import com.kuriousdragon.smartindex.source.telephony.testing.FakeTelephonyProviders
import com.kuriousdragon.smartindex.source.telephony.testing.addPart
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MmsPartsReaderTest {
    private val providers = FakeTelephonyProviders()
    private val reader = MmsPartsReader(providers)

    @Test
    fun `a batch is read with one query using mid IN with the ids inlined`() {
        providers.addPart(1, 10, text = "a")
        providers.addPart(2, 11, text = "b")
        reader.read(longArrayOf(10, 11, 12))

        assertThat(providers.queries).hasSize(1)
        val spec = providers.queries.single()
        assertThat(spec.uri.toString()).isEqualTo("content://mms/part")
        assertThat(spec.selection).isEqualTo("mid IN (10,11,12)")
        assertThat(spec.args).isEmpty()
        assertThat(spec.sortOrder).isEqualTo("mid ASC, seq ASC")
        assertThat(spec.projection).containsExactly("_id", "mid", "seq", "ct", "text").inOrder()
    }

    @Test
    fun `multi-part text is joined with a newline in seq order`() {
        providers.addPart(1, 10, seq = 2, text = "third")
        providers.addPart(2, 10, seq = 0, text = "first")
        providers.addPart(3, 10, seq = 1, text = "second")
        val content = reader.read(longArrayOf(10)).getValue(10)

        assertThat(content.body).isEqualTo("first\nsecond\nthird")
        assertThat(content.hasAttachment).isFalse()
    }

    @Test
    fun `SMIL is ignored and an image sets the attachment flag`() {
        providers.addPart(1, 10, seq = 0, contentType = "application/smil", text = "<smil/>")
        providers.addPart(2, 10, seq = 1, contentType = "image/jpeg")
        providers.addPart(3, 10, seq = 2, text = "caption")
        val content = reader.read(longArrayOf(10)).getValue(10)

        assertThat(content.body).isEqualTo("caption")
        assertThat(content.hasAttachment).isTrue()
    }

    @Test
    fun `other non-text types are attachments and text-only is not`() {
        providers.addPart(1, 10, contentType = "video/mp4")
        providers.addPart(2, 11, contentType = "audio/amr")
        providers.addPart(3, 12, contentType = "text/x-vcard")
        providers.addPart(4, 13, contentType = "application/pdf")
        providers.addPart(5, 14, text = "only text")
        val result = reader.read(longArrayOf(10, 11, 12, 13, 14))

        for (id in 10L..13L) assertThat(result.getValue(id).hasAttachment).isTrue()
        assertThat(result.getValue(10).body).isNull()
        assertThat(result.getValue(14).hasAttachment).isFalse()
    }

    @Test
    fun `a content type with a charset parameter is text and compared case-insensitively`() {
        providers.addPart(1, 10, seq = 0, contentType = "text/plain; charset=utf-8", text = "one")
        providers.addPart(2, 10, seq = 1, contentType = "TEXT/Plain", text = "two")
        val content = reader.read(longArrayOf(10)).getValue(10)

        assertThat(content.body).isEqualTo("one\ntwo")
        assertThat(content.hasAttachment).isFalse()
    }

    @Test
    fun `a null content type counts as text when it has text and is ignored otherwise`() {
        providers.addPart(1, 10, contentType = null, text = "loose text")
        providers.addPart(2, 11, contentType = null, text = null)
        val result = reader.read(longArrayOf(10, 11))

        assertThat(result.getValue(10).body).isEqualTo("loose text")
        assertThat(result.getValue(11)).isEqualTo(MmsContent.EMPTY)
    }

    @Test
    fun `a text part with null text falls back to openText on the part uri`() {
        providers.addPart(7, 10, text = null)
        providers.textParts["content://mms/part/7"] = "from the stream"
        assertThat(reader.read(longArrayOf(10)).getValue(10).body).isEqualTo("from the stream")
    }

    @Test
    fun `the openText fallback is capped at 64 KiB`() {
        providers.addPart(7, 10, text = null)
        providers.textParts["content://mms/part/7"] = "x".repeat(TelephonyConfig.MMS_TEXT_PART_MAX_BYTES + 100)
        val body = reader.read(longArrayOf(10)).getValue(10).body!!
        assertThat(body).hasLength(TelephonyConfig.MMS_TEXT_PART_MAX_BYTES)
    }

    @Test
    fun `a failed openText fallback and blank texts are dropped`() {
        providers.addPart(1, 10, seq = 0, text = null) // no stream registered
        providers.addPart(2, 10, seq = 1, text = "   ")
        providers.addPart(3, 10, seq = 2, text = "kept ")
        assertThat(reader.read(longArrayOf(10)).getValue(10).body).isEqualTo("kept ")
    }

    @Test
    fun `a message without parts is absent from the map and an empty batch runs no query`() {
        providers.addPart(1, 10, text = "x")
        assertThat(reader.read(longArrayOf(10, 99))).containsKey(10L)
        assertThat(reader.read(longArrayOf(10, 99))).doesNotContainKey(99L)
        providers.queries.clear()
        assertThat(reader.read(longArrayOf())).isEmpty()
        assertThat(providers.queries).isEmpty()
    }

    @Test
    fun `Devanagari text is kept exactly`() {
        val text = "होटल सी व्यू में बुकिंग"
        providers.addPart(1, 10, text = text)
        assertThat(reader.read(longArrayOf(10)).getValue(10).body).isEqualTo(text)
    }
}
