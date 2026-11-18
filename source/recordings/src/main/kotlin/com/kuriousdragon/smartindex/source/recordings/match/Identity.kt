package com.kuriousdragon.smartindex.source.recordings.match

import com.kuriousdragon.smartindex.core.model.PhoneNumberNormalizer
import com.kuriousdragon.smartindex.core.model.RecordingInputs
import com.kuriousdragon.smartindex.source.recordings.parse.NameFolding
import com.kuriousdragon.smartindex.source.recordings.parse.ParsedRecordingName

/** How well the counterpart in a recording's file name agrees with a call (LLD §4.4). */
enum class Identity(val score: Double, val label: String) {
    MATCH_NUMBER(1.0, "number"),
    MATCH_NAME(1.0, "name"),
    PARTIAL_NAME(0.5, "partial"),
    UNKNOWN(0.0, "time"),
    CONTRADICT_NUMBER(0.0, "contradict"),
    CONTRADICT_NAME(0.0, "contradict"),
    ;

    val contradicts: Boolean get() = this == CONTRADICT_NUMBER || this == CONTRADICT_NAME
    val isMatch: Boolean get() = this == MATCH_NUMBER || this == MATCH_NAME
}

data class RecordingIdentity(val numberNorm: String?, val last10: String?, val nameFolded: String?)

data class CallIdentity(val numberNorm: String?, val last10: String?, val nameFolded: String?)

class IdentityMatcher(private val numbers: PhoneNumberNormalizer) {
    /** normalize(number), last10(digitsOnly(number)), fold(name); all null when [parsed] is null. */
    fun ofRecording(parsed: ParsedRecordingName?): RecordingIdentity =
        if (parsed == null) {
            RecordingIdentity(null, null, null)
        } else {
            RecordingIdentity(
                numberNorm = numbers.normalize(parsed.number),
                last10 = last10(numbers.digitsOnly(parsed.number)),
                nameFolded = NameFolding.fold(parsed.name),
            )
        }

    fun ofCall(call: RecordingInputs.Call): CallIdentity =
        CallIdentity(
            numberNorm = call.numberNorm,
            last10 = last10(numbers.digitsOnly(call.numberRaw)),
            nameFolded = NameFolding.fold(call.contactName),
        )

    fun compare(r: RecordingIdentity, c: CallIdentity): Identity {
        if (r.numberNorm != null && c.numberNorm != null) {
            if (r.numberNorm == c.numberNorm) return Identity.MATCH_NUMBER
            if (r.last10 != null && r.last10 == c.last10) return Identity.MATCH_NUMBER
            return Identity.CONTRADICT_NUMBER
        }
        val f = r.nameFolded
        val n = c.nameFolded
        if (f != null && n != null) {
            if (f == n) return Identity.MATCH_NAME
            if (f.endsWith(" $n") || f.startsWith("$n ") || n.startsWith("$f ") || n.endsWith(" $f")) {
                return Identity.PARTIAL_NAME
            }
            return Identity.CONTRADICT_NAME
        }
        return Identity.UNKNOWN
    }

    companion object {
        fun last10(digits: String?): String? = digits?.takeIf { it.length >= 10 }?.takeLast(10)
    }
}
