package com.kuriousdragon.smartindex.source.recordings.match

import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.core.model.RecordingInputs
import com.kuriousdragon.smartindex.source.recordings.parse.ParsedRecordingName
import java.time.ZoneId
import java.time.ZonedDateTime

enum class AnchorTier(val score: Double, val label: String) {
    PARSED(1.0, "ts"),
    EVENT(0.5, "event"),
    MTIME(0.0, "mtime"),
}

data class Anchor(val tier: AnchorTier, val atMs: Long)

object AnchorResolver {
    /** All anchors of [rec], ordered by tier then time (LLD §4.3). Empty when none can be computed. */
    fun anchors(rec: RecordingInputs.Recording, parsed: ParsedRecordingName?, zone: ZoneId): List<Anchor> {
        val out = ArrayList<Anchor>(3)
        val parsedMs = ArrayList<Long>(2)
        if (parsed != null) {
            val local = parsed.localDateTime
            val offsets = zone.rules.getValidOffsets(local)
            if (offsets.isEmpty()) {
                parsedMs += ZonedDateTime.ofLocal(local, zone, null).toInstant().toEpochMilli()
            } else {
                offsets.forEach { parsedMs += local.toInstant(it).toEpochMilli() }
            }
            parsedMs.sorted().forEach { out += Anchor(AnchorTier.PARSED, it) }
        }
        rec.dates[DateKind.EVENT]?.let { event ->
            if (event !in parsedMs) out += Anchor(AnchorTier.EVENT, event)
        }
        val duration = rec.durationMs
        if (duration != null && duration > 0) {
            val finished = rec.dates[DateKind.MODIFIED] ?: rec.dates[DateKind.ADDED] ?: rec.primaryTs
            out += Anchor(AnchorTier.MTIME, finished - duration)
        }
        return out
    }
}
