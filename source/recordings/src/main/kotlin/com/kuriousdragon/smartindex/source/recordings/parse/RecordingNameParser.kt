package com.kuriousdragon.smartindex.source.recordings.parse

import java.text.Normalizer
import java.time.LocalDateTime

/** File name → [ParsedRecordingName], or null when no row of [patterns] matches with a valid date and time. */
class RecordingNameParser(
    private val patterns: List<RecordingNamePattern> = RecordingNamePatterns.TABLE,
) {
    fun parse(fileName: String): ParsedRecordingName? {
        val name = Normalizer.normalize(fileName.trim(), Normalizer.Form.NFC)
        if (name.isEmpty() || name.length > MAX_NAME_LENGTH) return null
        for (p in patterns) {
            val m = p.regex.matchEntire(name) ?: continue
            val local = localDateTime(p.dateDigits, m.group("date")!!, m.group("time")!!) ?: continue
            val (who, number) = splitWho(m.group("who").orEmpty(), m.group("number"))
            return ParsedRecordingName(p.id, who, number, local, m.group("copy") != null)
        }
        return null
    }

    /** Value of the named group, or null when it did not participate or the row's regex does not declare it. */
    private fun MatchResult.group(name: String): String? =
        try {
            groups[name]?.value
        } catch (_: IllegalArgumentException) {
            null
        }

    internal fun localDateTime(dateDigits: Int, date: String, time: String): LocalDateTime? {
        if (date.length != dateDigits || time.length != 6) return null
        val year = if (dateDigits == 6) 2000 + date.substring(0, 2).toInt() else date.substring(0, 4).toInt()
        if (year !in 2000..2099) return null
        val md = date.takeLast(4)
        return runCatching {
            LocalDateTime.of(
                year,
                md.substring(0, 2).toInt(),
                md.substring(2, 4).toInt(),
                time.substring(0, 2).toInt(),
                time.substring(2, 4).toInt(),
                time.substring(4, 6).toInt(),
            )
        }.getOrNull()
    }

    /** Returns (name, number). */
    internal fun splitWho(rawWho: String, rawNumber: String?): Pair<String?, String?> {
        var who = stripPrefix(rawWho.trim().trim('_').trim())
        var number = rawNumber?.trim()?.ifEmpty { null }
        if (number == null) {
            if (RecordingNamePatterns.NUMBER_ONLY.matches(who) && who.count { it.isDigit() } >= 3) {
                number = who
                who = ""
            } else {
                val m = RecordingNamePatterns.TRAILING_PAREN_NUMBER.find(who)
                    ?: RecordingNamePatterns.TRAILING_NUMBER.find(who)
                if (m != null) {
                    number = m.groupValues[1].trim()
                    who = who.substring(0, m.range.first).trim()
                }
            }
        }
        who = stripPrefix(who)
        val name = who.takeIf { it.isNotBlank() && NameFolding.fold(it) !in RecordingNamePatterns.PLACEHOLDERS }
        return name to number
    }

    private fun stripPrefix(s: String): String {
        for (p in RecordingNamePatterns.KNOWN_PREFIXES) {
            if (s.regionMatches(0, p, 0, p.length, ignoreCase = true) &&
                (s.length == p.length || s[p.length].isWhitespace() || s[p.length] == '_')
            ) {
                return s.substring(p.length).trim().trim('_').trim()
            }
        }
        return s
    }

    companion object {
        const val MAX_NAME_LENGTH = 255
    }
}
