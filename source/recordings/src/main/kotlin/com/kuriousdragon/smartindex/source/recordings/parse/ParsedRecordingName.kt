package com.kuriousdragon.smartindex.source.recordings.parse

import java.time.LocalDateTime

/** One row of the pattern table. [regex] is matched against the whole NFC file name (matchEntire). */
data class RecordingNamePattern(
    val id: String,
    val regex: Regex, // named groups: who (required), number (optional), date, time, copy (optional), ext
    val dateDigits: Int, // 6 = yyMMdd (year 2000 + yy), 8 = yyyyMMdd
    val example: String,
)

/**
 * What a Samsung call-recording file name says.
 * @property name counterpart as written, known prefix stripped; null when absent or a placeholder ("Unknown").
 * @property number counterpart number as written (spaces etc. kept); null when absent.
 * @property localDateTime wall-clock time of recording start, **device local time** (no zone in the name).
 * @property isCopy the name has a duplicate suffix like " (1)".
 */
data class ParsedRecordingName(
    val patternId: String,
    val name: String?,
    val number: String?,
    val localDateTime: LocalDateTime,
    val isCopy: Boolean,
)
