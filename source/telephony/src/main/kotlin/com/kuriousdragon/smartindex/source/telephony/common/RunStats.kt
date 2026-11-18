package com.kuriousdragon.smartindex.source.telephony.common

import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.core.model.Source

enum class RunError { PERMISSION_REVOKED, PROVIDER_UNAVAILABLE, PROVIDER_ERROR }

/** Mutable counters of one indexer run. Holds counts and exception class names only, never content. */
class RunStatsBuilder(val source: Source, val mode: Mode) {
    var read = 0
    var written = 0

    /** Rows removed by the final reconcile. */
    var deleted = 0

    /** Reason → count of dropped rows, e.g. `no_date`, `unknown_type`. */
    val skipped: MutableMap<String, Int> = linkedMapOf()

    /** Non-skip counters, e.g. `addr_budget`, `unknown_type_value_<n>`. */
    var notes: MutableMap<String, Int> = linkedMapOf()
    var error: RunError? = null
    var errorClass: String? = null

    fun skip(reason: String) {
        skipped.merge(reason, 1, Int::plus)
    }

    fun note(key: String) {
        notes.merge(key, 1, Int::plus)
    }

    fun fail(e: RunError, t: Throwable) {
        error = e
        errorClass = t.javaClass.simpleName
    }

    fun build(durationMs: Long): TelephonyRunStats = TelephonyRunStats(
        source = source,
        mode = mode,
        read = read,
        written = written,
        deleted = deleted,
        skipped = LinkedHashMap(skipped),
        notes = LinkedHashMap(notes),
        durationMs = durationMs,
        error = error,
        errorClass = errorClass,
    )
}

data class TelephonyRunStats(
    val source: Source,
    val mode: Mode,
    val read: Int,
    val written: Int,
    val skipped: Map<String, Int>,
    val notes: Map<String, Int>,
    val durationMs: Long,
    val error: RunError?,
    val errorClass: String?,
    val deleted: Int = 0,
)
