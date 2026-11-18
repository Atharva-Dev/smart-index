package com.kuriousdragon.smartindex.tools.seeder.media

import com.kuriousdragon.smartindex.tools.seeder.FreshType
import com.kuriousdragon.smartindex.tools.seeder.gen.MediaKind
import com.kuriousdragon.smartindex.tools.seeder.gen.Payload
import com.kuriousdragon.smartindex.tools.seeder.gen.SeedFile
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Builds the one-file plan of `cmd=fresh` for the file types (NFR-005): named after the token, dated [nowMs]. */
object FreshFiles {
    private val STAMP = DateTimeFormatter.ofPattern("yyMMdd_HHmmss")
    const val RECORDING_MS = 30_000L

    fun plan(type: FreshType, token: String, nowMs: Long, zone: ZoneId): SeedFile? = when (type) {
        FreshType.PHOTO -> SeedFile(
            0, MediaKind.PHOTO, "DCIM/Camera/", "fresh-$token.jpg", "image/jpeg", 1_500L, nowMs, nowMs, null,
            Payload.Jpeg(0),
        )

        FreshType.VIDEO -> SeedFile(
            0, MediaKind.VIDEO, "DCIM/Camera/", "fresh-$token.mp4", "video/mp4", 30_000L, nowMs, nowMs, 5_000L,
            Payload.Mp4(0),
        )

        FreshType.DOCUMENT -> SeedFile(
            0, MediaKind.DOCUMENT, "Download/", "fresh-$token.pdf", "application/pdf", 4_096L, null, nowMs, null,
            Payload.Doc("pdf", "Fresh document $token"),
        )

        FreshType.OTHER -> SeedFile(
            0, MediaKind.OTHER, "Download/", "fresh-$token.zip", "application/zip", 2_048L, null, nowMs, null,
            Payload.Doc("zip", null),
        )

        FreshType.RECORDING -> {
            val name = "Call recording Fresh ${token}_${STAMP.format(Instant.ofEpochMilli(nowMs).atZone(zone))}.m4a"
            SeedFile(
                0, MediaKind.RECORDING, "Recordings/Call/", name, "audio/mp4", RECORDING_MS * 2, null, nowMs,
                RECORDING_MS, Payload.M4a,
            )
        }

        else -> null
    }
}
