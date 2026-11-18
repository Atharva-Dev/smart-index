package com.kuriousdragon.smartindex.sync.work

import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.sync.SyncReason

/** How an existing unique work is treated when re-armed: maps to WorkManager KEEP, REPLACE, APPEND_OR_REPLACE. */
enum class ArmPolicy { KEEP, REPLACE, APPEND }

/** All WorkManager scheduling of orchestration runs (LLD — Indexing orchestration §3.4, §5.3). */
interface SyncScheduler {
    /** Enqueues a run with two-slot coalescing. */
    suspend fun enqueueSync(reason: SyncReason, expedited: Boolean)

    /** Ensures the 15-minute periodic backstop (`sidx.periodic`, UPDATE). */
    fun ensurePeriodic()

    /** Arms the content-trigger work (`sidx.trigger`) for [groups]; cancels it when there are no URIs. */
    fun armTriggers(groups: Set<Group>, policy: ArmPolicy)
}
