package com.kuriousdragon.smartindex.feature.home.format

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.Field
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.MsgKind
import com.kuriousdragon.smartindex.core.model.ResultItem
import com.kuriousdragon.smartindex.feature.home.model.DeviceEnv
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** Europe/London, Locale.UK, fixed today = Tue 2026-11-03 (a GMT day, so London equals UTC). */
internal fun testEnv(is24Hour: Boolean = true) =
    DeviceEnv(ZoneId.of("Europe/London"), Locale.UK, is24Hour, LocalDate.of(2026, 11, 3))

internal fun resources(): Resources = ApplicationProvider.getApplicationContext<Context>().resources

internal fun londonMs(date: String, time: String): Long =
    ZonedDateTime.parse("${date}T$time:00+00:00[Europe/London]").toInstant().toEpochMilli()

@Suppress("LongParameterList")
internal fun item(
    type: ItemType,
    id: Long = 1,
    title: String = "title",
    time: Long = londonMs("2026-11-03", "09:15"),
    folder: String? = null,
    sizeBytes: Long? = null,
    durationMs: Long? = null,
    ext: String? = null,
    direction: Direction? = null,
    msgKind: MsgKind? = null,
    hasAttachment: Boolean = false,
    contactName: String? = null,
    number: String? = null,
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
    contentUri = null,
    snippet = snippet,
    matches = matches,
    linkedRecordingId = linkedRecordingId,
)
