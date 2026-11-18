package com.kuriousdragon.smartindex.sync

import com.kuriousdragon.smartindex.core.model.SourceRunResult

/** Why a run started (logging and work input only; the plan is derived from durable state). */
enum class SyncReason { REINDEX, CATCH_UP, OBSERVER, CONTENT_TRIGGER, PERIODIC, PERMISSIONS }

/** Why a run did nothing. */
enum class SkipReason { NOT_ENABLED, STORAGE_LOW, NOTHING_USABLE }

/** Result of one orchestration pass. */
data class SyncOutcome(
    val skipped: SkipReason? = null,
    val results: List<SourceRunResult> = emptyList(),
    val matched: Boolean = false,
    val linkError: String? = null,
)

/** Runs one orchestration pass; implemented by IndexCoordinator, faked in worker tests. */
fun interface SyncRunner {
    suspend fun sync(reason: SyncReason): SyncOutcome
}
