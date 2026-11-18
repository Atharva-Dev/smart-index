package com.kuriousdragon.smartindex.source.media.run

import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.core.model.RunResult
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceRunResult
import com.kuriousdragon.smartindex.core.model.SyncErrors
import java.util.Locale

enum class MediaRunError { PERMISSION_REVOKED, PROVIDER_UNAVAILABLE, PROVIDER_ERROR, WRITE_FAILED }

/** Counters of one run. Holds counts and exception class names only, never content (LLD §3.3.3, §6.2). */
class MediaRunStats(val mode: Mode) {
    var total = 0
    var read = 0
    var written = 0
    var deleted = 0

    /** "no_id", "bad_row", "map_error". */
    val skipped: MutableMap<String, Int> = linkedMapOf()

    /** `Exclusion.name`, "type_not_allowed". */
    val excluded: MutableMap<String, Int> = linkedMapOf()
    var error: MediaRunError? = null
    var errorClass: String? = null

    fun skip(reason: String) {
        skipped.merge(reason, 1, Int::plus)
    }

    fun exclude(reason: String) {
        excluded.merge(reason, 1, Int::plus)
    }

    fun fail(e: MediaRunError, t: Throwable?) {
        error = e
        errorClass = t?.let { SyncErrors.code(it) } ?: "none"
    }

    /** "media_store:<error lower-case>:<errorClass>", e.g. "media_store:write_failed:STORAGE_FULL"; null on success. */
    fun errorCode(): String? = error?.let { "media_store:${it.name.lowercase(Locale.ROOT)}:$errorClass" }

    /** The only place that builds the orchestration result: adapt here if Sync.kt changes. */
    fun toRunResult(): RunResult = RunResult(
        listOf(
            SourceRunResult(
                source = Source.MEDIA_STORE,
                mode = mode,
                written = written,
                skipped = skipped.values.sum(),
                deleted = deleted,
                error = errorCode(),
            ),
        ),
    )
}
