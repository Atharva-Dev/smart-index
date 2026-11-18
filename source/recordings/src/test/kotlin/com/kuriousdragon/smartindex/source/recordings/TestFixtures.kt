package com.kuriousdragon.smartindex.source.recordings

import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.core.model.DefaultPhoneNumberNormalizer
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.E164Formatter
import com.kuriousdragon.smartindex.core.model.PhoneNumberNormalizer
import com.kuriousdragon.smartindex.core.model.RecordingInputs
import java.time.LocalDateTime
import java.time.ZoneId

val IST: ZoneId = ZoneId.of("Asia/Kolkata")
val LONDON: ZoneId = ZoneId.of("Europe/London")

/** Keeps digits; 10 digits -> +91<d>; 91 + 10 digits -> +<d>; a leading + -> +<digits>; else null. */
object FakeE164 : E164Formatter {
    override fun formatE164(number: String, countryIso: String): String? {
        val t = number.trim()
        val digits = t.filter { it in '0'..'9' }
        return when {
            t.startsWith("+") && digits.isNotEmpty() -> "+$digits"
            digits.length == 10 -> "+91$digits"
            digits.length == 12 && digits.startsWith("91") -> "+$digits"
            else -> null
        }
    }
}

val numberNormalizer: PhoneNumberNormalizer = DefaultPhoneNumberNormalizer(country = { "IN" }, e164 = FakeE164)

fun epoch(local: String, zone: ZoneId = IST): Long =
    LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()

fun call(
    id: Long,
    local: String,
    durS: Long?,
    number: String? = "+919876543210",
    name: String? = "Rahul",
    dir: Direction? = Direction.INCOMING,
    zone: ZoneId = IST,
): RecordingInputs.Call =
    RecordingInputs.Call(
        id = id,
        primaryTs = epoch(local, zone),
        durationMs = durS?.let { it * 1000 },
        numberNorm = numberNormalizer.normalize(number),
        numberRaw = number,
        contactName = name,
        direction = dir,
    )

fun recording(
    id: Long,
    title: String,
    durS: Long?,
    modifiedLocal: String,
    sizeBytes: Long? = 1000,
    zone: ZoneId = IST,
): RecordingInputs.Recording {
    val modified = epoch(modifiedLocal, zone)
    return RecordingInputs.Recording(
        id = id,
        title = title,
        folder = "Recordings/Call",
        primaryTs = modified,
        dates = mapOf(DateKind.MODIFIED to modified),
        durationMs = durS?.let { it * 1000 },
        sizeBytes = sizeBytes,
    )
}
