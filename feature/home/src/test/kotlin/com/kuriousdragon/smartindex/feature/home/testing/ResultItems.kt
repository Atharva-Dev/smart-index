package com.kuriousdragon.smartindex.feature.home.testing

import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.Field
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.MsgKind
import com.kuriousdragon.smartindex.core.model.ResultItem
import java.time.ZonedDateTime

/** Epoch ms of [date] ("2026-11-03") at [time] ("09:15") in Europe/London. */
internal fun londonTime(date: String, time: String): Long =
    ZonedDateTime.parse("${date}T$time:00+00:00[Europe/London]").toInstant().toEpochMilli()

/** A [ResultItem] with sensible defaults for every type. */
@Suppress("LongParameterList")
internal fun resultItem(
    id: Long,
    type: ItemType = ItemType.PHOTO,
    title: String = "item$id",
    time: Long = londonTime("2026-11-03", "09:15"),
    folder: String? = null,
    sizeBytes: Long? = null,
    durationMs: Long? = null,
    ext: String? = null,
    direction: Direction? = null,
    msgKind: MsgKind? = null,
    hasAttachment: Boolean = false,
    contactName: String? = null,
    number: String? = null,
    contentUri: String? = null,
    snippet: String? = null,
    matches: Map<Field, List<IntRange>> = emptyMap(),
    linkedRecordingId: Long? = null,
) = ResultItem(
    id = id,
    type = type,
    title = title,
    time = time,
    folder = folder,
    sizeBytes = sizeBytes,
    durationMs = durationMs,
    ext = ext,
    direction = direction,
    msgKind = msgKind,
    hasAttachment = hasAttachment,
    contactName = contactName,
    number = number,
    contentUri = contentUri,
    snippet = snippet,
    matches = matches,
    linkedRecordingId = linkedRecordingId,
)

internal fun photo(id: Long, time: Long = londonTime("2026-11-03", "09:15")) =
    resultItem(id, ItemType.PHOTO, "IMG_$id.jpg", time, folder = "DCIM/Camera", ext = "jpg")

internal fun video(id: Long, time: Long = londonTime("2026-11-03", "09:15")) =
    resultItem(id, ItemType.VIDEO, "VID_$id.mp4", time, folder = "DCIM/Camera", ext = "mp4", durationMs = 12_000)

internal fun document(id: Long, time: Long = londonTime("2026-11-03", "09:15")) =
    resultItem(id, ItemType.DOCUMENT, "doc$id.pdf", time, folder = "Download", ext = "pdf", sizeBytes = 2_048)

internal fun otherFile(id: Long, time: Long = londonTime("2026-11-03", "09:15")) =
    resultItem(id, ItemType.OTHER_FILE, "file$id.zip", time, folder = "Download", ext = "zip", sizeBytes = 4_096)

internal fun call(id: Long, time: Long = londonTime("2026-11-03", "09:15"), linkedRecordingId: Long? = null) =
    resultItem(
        id, ItemType.CALL, "Rahul", time, direction = Direction.INCOMING, contactName = "Rahul",
        number = "+919812345678", durationMs = 65_000, linkedRecordingId = linkedRecordingId,
    )

internal fun recording(id: Long, time: Long = londonTime("2026-11-03", "09:15")) =
    resultItem(
        id, ItemType.RECORDING, "Rahul_261103_091500.m4a", time, folder = "Recordings/Call", ext = "m4a",
        durationMs = 65_000, contactName = "Rahul", number = "+919812345678",
    )

internal fun message(id: Long, time: Long = londonTime("2026-11-03", "09:15")) =
    resultItem(
        id, ItemType.MESSAGE, "Rahul", time, direction = Direction.RECEIVED, msgKind = MsgKind.SMS,
        contactName = "Rahul", number = "+919812345678", snippet = "See you at the hotel",
    )
