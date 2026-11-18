package com.kuriousdragon.smartindex.feature.home.format

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.Field
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.MsgKind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RowFormatterTest {
    private val res = resources()
    private val env = testEnv()

    private fun ui(item: com.kuriousdragon.smartindex.core.model.ResultItem) = RowFormatter.format(item, env, res)

    // Files

    @Test
    fun `photo row`() {
        val row = ui(
            item(
                ItemType.PHOTO,
                id = 7,
                title = "20261103_091512.jpg",
                folder = "DCIM/Camera",
                sizeBytes = (3.2 * 1024 * 1024).toLong(),
                ext = "jpg",
            ),
        )
        assertThat(row.id).isEqualTo(7)
        assertThat(row.title.text).isEqualTo("20261103_091512.jpg")
        assertThat(row.titleEllipsis).isEqualTo(TitleEllipsis.MIDDLE)
        assertThat(row.time).isEqualTo("09:15")
        assertThat(row.meta.text).isEqualTo("Camera · 3.2 MB")
        assertThat(row.metaIcon).isNull()
        assertThat(row.snippet).isNull()
        assertThat(row.extLabel).isNull()
        assertThat(row.videoDuration).isNull()
        assertThat(row.trailing).isEqualTo(Trailing.MORE)
        assertThat(row.recordingId).isNull()
        assertThat(row.showAttachment).isFalse()
        assertThat(row.actions).containsExactly(RowAction.OPEN_WITH, RowAction.MORE_ACTIONS).inOrder()
    }

    @Test
    fun `video row has a duration pill and a duration in the meta line`() {
        val row = ui(
            item(
                ItemType.VIDEO,
                title = "clip.mp4",
                folder = "DCIM/Camera",
                durationMs = 252_000,
                sizeBytes = 48L * 1024 * 1024,
                ext = "mp4",
            ),
        )
        assertThat(row.meta.text).isEqualTo("Camera · 4:12 · 48.0 MB")
        assertThat(row.videoDuration).isEqualTo("4:12")
        assertThat(row.extLabel).isNull()
        assertThat(row.trailing).isEqualTo(Trailing.MORE)
    }

    @Test
    fun `video without duration has no pill and no duration part`() {
        val row = ui(item(ItemType.VIDEO, title = "clip.mp4", folder = "Camera", sizeBytes = 2048))
        assertThat(row.videoDuration).isNull()
        assertThat(row.meta.text).isEqualTo("Camera · 2.0 KB")
    }

    @Test
    fun `document row has an uppercase ext label and a title cut in the middle`() {
        val row =
            ui(item(ItemType.DOCUMENT, title = "report.pdf", folder = "Download", sizeBytes = 240L * 1024, ext = "pdf"))
        assertThat(row.extLabel).isEqualTo("PDF")
        assertThat(row.titleEllipsis).isEqualTo(TitleEllipsis.MIDDLE)
        assertThat(row.meta.text).isEqualTo("Download · 240 KB")
    }

    @Test
    fun `ext label is limited to five characters`() {
        val row = ui(item(ItemType.OTHER_FILE, title = "data.torrentfile", ext = "torrentfile"))
        assertThat(row.extLabel).isEqualTo("TORRE")
    }

    @Test
    fun `ext label falls back to the file name extension`() {
        assertThat(ui(item(ItemType.OTHER_FILE, title = "archive.zip", ext = null)).extLabel).isEqualTo("ZIP")
        assertThat(ui(item(ItemType.OTHER_FILE, title = "README", ext = null)).extLabel).isNull()
    }

    @Test
    fun `document with an unknown folder omits the folder part`() {
        val nullFolder = ui(item(ItemType.DOCUMENT, title = "a.pdf", folder = null, sizeBytes = 1024, ext = "pdf"))
        assertThat(nullFolder.meta.text).isEqualTo("1.0 KB")
        val blankFolder = ui(item(ItemType.DOCUMENT, title = "a.pdf", folder = "  ", sizeBytes = 1024, ext = "pdf"))
        assertThat(blankFolder.meta.text).isEqualTo("1.0 KB")
        val nothing = ui(item(ItemType.DOCUMENT, title = "a.pdf", folder = null, sizeBytes = null, ext = "pdf"))
        assertThat(nothing.meta.text).isEmpty()
    }

    @Test
    fun `folder shows its last segment and the folder highlight is shifted onto it`() {
        val row = ui(
            item(
                ItemType.PHOTO,
                title = "a.jpg",
                folder = "DCIM/Camera",
                sizeBytes = 100,
                matches = mapOf(Field.FOLDER to listOf(5..7)),
            ),
        )
        assertThat(row.meta.text).isEqualTo("Camera · 100 B")
        assertThat(row.meta.ranges).containsExactly(0..2)
    }

    @Test
    fun `folder highlight in a parent segment is dropped`() {
        val row = ui(
            item(
                ItemType.PHOTO,
                title = "a.jpg",
                folder = "DCIM/Camera",
                sizeBytes = 100,
                matches = mapOf(Field.FOLDER to listOf(0..3)),
            ),
        )
        assertThat(row.meta.ranges).isEmpty()
    }

    @Test
    fun `folder match crossing into the last segment is clipped to it`() {
        val row = ui(
            item(
                ItemType.PHOTO,
                title = "a.jpg",
                folder = "DCIM/Camera",
                sizeBytes = 100,
                matches = mapOf(Field.FOLDER to listOf(3..6)),
            ),
        )
        assertThat(row.meta.ranges).containsExactly(0..1)
    }

    @Test
    fun `ext match is mapped onto the title tail and merged with title matches`() {
        val row = ui(
            item(
                ItemType.DOCUMENT,
                title = "report.pdf",
                ext = "pdf",
                matches = mapOf(Field.TITLE to listOf(0..1), Field.EXT to listOf(0..2)),
            ),
        )
        assertThat(row.title.ranges).containsExactly(0..1, 7..9).inOrder()
    }

    @Test
    fun `ext match is dropped when the title does not end in the extension`() {
        val row = ui(
            item(
                ItemType.DOCUMENT,
                title = "report",
                ext = "pdf",
                matches = mapOf(Field.EXT to listOf(0..2)),
            ),
        )
        assertThat(row.title.ranges).isEmpty()
    }

    @Test
    fun `ext match mapping ignores case`() {
        val row = ui(
            item(
                ItemType.DOCUMENT,
                title = "REPORT.PDF",
                ext = "pdf",
                matches = mapOf(Field.EXT to listOf(0..0)),
            ),
        )
        assertThat(row.title.ranges).containsExactly(7..7)
    }

    @Test
    fun `no range is applied to a field it does not belong to`() {
        val row = ui(
            item(
                ItemType.CALL,
                title = "+44 7700 900123",
                contactName = "Rahul",
                direction = Direction.INCOMING,
                matches = mapOf(
                    Field.NUMBER to listOf(0..3),
                    Field.TITLE to listOf(0..2),
                    Field.CONTACT_NAME to listOf(1..2),
                ),
            ),
        )
        assertThat(row.title.text).isEqualTo("Rahul")
        assertThat(row.title.ranges).containsExactly(1..2)
    }

    @Test
    fun `title ranges are clamped to the title`() {
        val row = ui(item(ItemType.PHOTO, title = "a.jpg", matches = mapOf(Field.TITLE to listOf(3..40))))
        assertThat(row.title.ranges).containsExactly(3..4)
    }

    @Test
    fun `surrogate pair in a title keeps UTF-16 ranges`() {
        val title = "a😀b.png"
        val row = ui(item(ItemType.PHOTO, title = title, ext = "png", matches = mapOf(Field.TITLE to listOf(1..2))))
        assertThat(row.title.ranges).containsExactly(1..2)
    }

    // Calls

    @Test
    fun `incoming call with a contact and a linked recording`() {
        val row = ui(
            item(
                ItemType.CALL,
                id = 3,
                title = "+44 7700 900123",
                contactName = "Rahul",
                number = "+447700900123",
                direction = Direction.INCOMING,
                durationMs = 252_000,
                linkedRecordingId = 9,
            ),
        )
        assertThat(row.title.text).isEqualTo("Rahul")
        assertThat(row.titleEllipsis).isEqualTo(TitleEllipsis.END)
        assertThat(row.meta.text).isEqualTo("Incoming · 4 min 12 s")
        assertThat(row.metaIcon).isEqualTo(MetaIcon.INCOMING)
        assertThat(row.trailing).isEqualTo(Trailing.PLAY)
        assertThat(row.recordingId).isEqualTo(9)
        assertThat(row.snippet).isNull()
        assertThat(row.actions)
            .containsExactly(RowAction.CALL_BACK, RowAction.OPEN_CONTACT, RowAction.PLAY_RECORDING).inOrder()
    }

    @Test
    fun `outgoing call without a contact shows the formatted number`() {
        val row = ui(
            item(
                ItemType.CALL,
                title = "+44 7700 900123",
                number = "+447700900123",
                direction = Direction.OUTGOING,
                durationMs = 38_000,
                matches = mapOf(Field.TITLE to listOf(1..2)),
            ),
        )
        assertThat(row.title.text).isEqualTo("+44 7700 900123")
        assertThat(row.title.ranges).containsExactly(1..2)
        assertThat(row.meta.text).isEqualTo("Outgoing · 38 s")
        assertThat(row.metaIcon).isEqualTo(MetaIcon.OUTGOING)
        assertThat(row.trailing).isEqualTo(Trailing.NONE)
        assertThat(row.recordingId).isNull()
        assertThat(row.actions).containsExactly(RowAction.CALL_BACK, RowAction.ADD_CONTACT).inOrder()
    }

    @Test
    fun `missed rejected and blocked calls`() {
        assertThat(ui(item(ItemType.CALL, direction = Direction.MISSED)).meta.text).isEqualTo("Missed call")
        assertThat(ui(item(ItemType.CALL, direction = Direction.MISSED)).metaIcon).isEqualTo(MetaIcon.MISSED)
        assertThat(ui(item(ItemType.CALL, direction = Direction.REJECTED)).meta.text).isEqualTo("Rejected call")
        assertThat(ui(item(ItemType.CALL, direction = Direction.REJECTED)).metaIcon).isEqualTo(MetaIcon.REJECTED)
        assertThat(ui(item(ItemType.CALL, direction = Direction.BLOCKED)).meta.text).isEqualTo("Blocked call")
        assertThat(ui(item(ItemType.CALL, direction = Direction.BLOCKED)).metaIcon).isEqualTo(MetaIcon.BLOCKED)
    }

    @Test
    fun `missed call never shows a duration`() {
        assertThat(ui(item(ItemType.CALL, direction = Direction.MISSED, durationMs = 5000)).meta.text)
            .isEqualTo("Missed call")
    }

    @Test
    fun `call without a duration shows just the direction`() {
        assertThat(ui(item(ItemType.CALL, direction = Direction.INCOMING, durationMs = null)).meta.text)
            .isEqualTo("Incoming")
        assertThat(ui(item(ItemType.CALL, direction = Direction.OUTGOING, durationMs = null)).meta.text)
            .isEqualTo("Outgoing")
    }

    @Test
    fun `call with a zero duration shows 0 s`() {
        assertThat(ui(item(ItemType.CALL, direction = Direction.INCOMING, durationMs = 0)).meta.text)
            .isEqualTo("Incoming · 0 s")
    }

    @Test
    fun `call without a number has no call back or contact action`() {
        val row = ui(item(ItemType.CALL, direction = Direction.MISSED, number = null, contactName = "Rahul"))
        assertThat(row.actions).isEmpty()
    }

    // Recordings

    @Test
    fun `recording title prefers contact name then number then title`() {
        val named = ui(item(ItemType.RECORDING, title = "Call_1.m4a", contactName = "Rahul", number = "+44 1"))
        assertThat(named.title.text).isEqualTo("Rahul")
        val numbered = ui(
            item(
                ItemType.RECORDING,
                title = "Call_1.m4a",
                number = "+44 1",
                matches = mapOf(Field.NUMBER to listOf(1..2), Field.TITLE to listOf(0..3)),
            ),
        )
        assertThat(numbered.title.text).isEqualTo("+44 1")
        assertThat(numbered.title.ranges).containsExactly(1..2)
        val titled = ui(
            item(ItemType.RECORDING, title = "Call_1.m4a", ext = "m4a", matches = mapOf(Field.TITLE to listOf(0..3))),
        )
        assertThat(titled.title.text).isEqualTo("Call_1.m4a")
        assertThat(titled.title.ranges).containsExactly(0..3)
    }

    @Test
    fun `recording row`() {
        val row = ui(
            item(
                ItemType.RECORDING,
                id = 12,
                title = "Call_1.m4a",
                contactName = "Rahul",
                durationMs = 252_000,
                ext = "m4a",
                folder = "Recordings/Call",
            ),
        )
        assertThat(row.meta.text).isEqualTo("Call recording · 4:12")
        assertThat(row.metaIcon).isEqualTo(MetaIcon.RECORDING)
        assertThat(row.extLabel).isEqualTo("M4A")
        assertThat(row.trailing).isEqualTo(Trailing.PLAY)
        assertThat(row.recordingId).isEqualTo(12)
        assertThat(row.titleEllipsis).isEqualTo(TitleEllipsis.END)
        assertThat(row.actions).containsExactly(RowAction.PLAY, RowAction.OPEN_WITH_AUDIO).inOrder()
    }

    @Test
    fun `recording without a duration`() {
        val row = ui(item(ItemType.RECORDING, durationMs = null))
        assertThat(row.meta.text).isEqualTo("Call recording")
    }

    // Messages

    @Test
    fun `received sms with a snippet and highlights`() {
        val row = ui(
            item(
                ItemType.MESSAGE,
                title = "+44 7700 900123",
                contactName = "Rahul",
                direction = Direction.RECEIVED,
                msgKind = MsgKind.SMS,
                snippet = "…hotel booking confirmed",
                matches = mapOf(Field.SNIPPET to listOf(1..5), Field.CONTACT_NAME to listOf(0..1)),
            ),
        )
        assertThat(row.title.text).isEqualTo("Rahul")
        assertThat(row.title.ranges).containsExactly(0..1)
        assertThat(row.titleEllipsis).isEqualTo(TitleEllipsis.END)
        assertThat(row.meta.text).isEqualTo("Received · SMS")
        assertThat(row.metaIcon).isEqualTo(MetaIcon.SMS_RECEIVED)
        assertThat(row.snippet?.text).isEqualTo("…hotel booking confirmed")
        assertThat(row.snippet?.ranges).containsExactly(1..5)
        assertThat(row.trailing).isEqualTo(Trailing.NONE)
        assertThat(row.showAttachment).isFalse()
        assertThat(row.actions).isEmpty()
    }

    @Test
    fun `sent sms and mms variants`() {
        assertThat(ui(item(ItemType.MESSAGE, direction = Direction.SENT, msgKind = MsgKind.SMS)).meta.text)
            .isEqualTo("Sent · SMS")
        assertThat(ui(item(ItemType.MESSAGE, direction = Direction.SENT, msgKind = MsgKind.SMS)).metaIcon)
            .isEqualTo(MetaIcon.SMS_SENT)
        assertThat(ui(item(ItemType.MESSAGE, direction = Direction.RECEIVED, msgKind = MsgKind.MMS)).meta.text)
            .isEqualTo("Received · MMS")
        assertThat(ui(item(ItemType.MESSAGE, direction = Direction.SENT, msgKind = MsgKind.MMS)).meta.text)
            .isEqualTo("Sent · MMS")
    }

    @Test
    fun `message without a direction shows only the kind`() {
        val sms = ui(item(ItemType.MESSAGE, direction = null, msgKind = MsgKind.SMS))
        assertThat(sms.meta.text).isEqualTo("SMS")
        assertThat(sms.metaIcon).isNull()
        assertThat(ui(item(ItemType.MESSAGE, direction = null, msgKind = MsgKind.MMS)).meta.text).isEqualTo("MMS")
    }

    @Test
    fun `mms with an attachment and no text shows the placeholder`() {
        val row = ui(
            item(
                ItemType.MESSAGE,
                direction = Direction.RECEIVED,
                msgKind = MsgKind.MMS,
                hasAttachment = true,
                snippet = null,
                matches = mapOf(Field.SNIPPET to listOf(0..2)),
            ),
        )
        assertThat(row.showAttachment).isTrue()
        assertThat(row.snippet?.text).isEqualTo("(No text)")
        assertThat(row.snippet?.ranges).isEmpty()
    }

    @Test
    fun `mms with an attachment and text keeps the text`() {
        val row = ui(item(ItemType.MESSAGE, msgKind = MsgKind.MMS, hasAttachment = true, snippet = "photo attached"))
        assertThat(row.showAttachment).isTrue()
        assertThat(row.snippet?.text).isEqualTo("photo attached")
    }

    @Test
    fun `message without a contact falls back to the title`() {
        val row = ui(item(ItemType.MESSAGE, title = "Unknown number", contactName = null))
        assertThat(row.title.text).isEqualTo("Unknown number")
    }

    @Test
    fun `message snippet with a surrogate pair keeps UTF-16 ranges`() {
        val text = "hi 😀 there"
        val row = ui(item(ItemType.MESSAGE, snippet = text, matches = mapOf(Field.SNIPPET to listOf(3..4))))
        assertThat(row.snippet?.ranges).containsExactly(3..4)
    }

    @Test
    fun `time follows the 12 hour setting`() {
        val row = RowFormatter.format(item(ItemType.PHOTO), testEnv(is24Hour = false), res)
        assertThat(row.time).isEqualTo("9:15 AM")
    }

    @Test
    fun `every item type formats without throwing`() {
        for (type in ItemType.entries) {
            val row = ui(item(type))
            assertThat(row.type).isEqualTo(type)
            assertThat(row.a11yLabel).isNotEmpty()
        }
    }
}
