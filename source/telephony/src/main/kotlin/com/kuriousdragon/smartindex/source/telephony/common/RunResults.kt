package com.kuriousdragon.smartindex.source.telephony.common

import com.kuriousdragon.smartindex.core.model.RunResult
import com.kuriousdragon.smartindex.core.model.SourceRunResult
import java.util.Locale

/**
 * The only place in this module that builds Sync.kt's [RunResult].
 * Success ⇔ `error == null`. The error text is `"<source>:<error>:<errorClass>"` in lower case, except the
 * class name — no message, no numbers.
 */
fun TelephonyRunStats.toRunResult(): RunResult = RunResult(
    listOf(
        SourceRunResult(
            source = source,
            mode = mode,
            written = written,
            skipped = skipped.values.sum(),
            deleted = deleted,
            error = error?.let {
                "${source.name.lowercase(Locale.ROOT)}:${it.name.lowercase(Locale.ROOT)}:${errorClass ?: "Unknown"}"
            },
        ),
    ),
)
