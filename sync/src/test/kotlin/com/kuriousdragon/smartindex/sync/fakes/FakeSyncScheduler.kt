package com.kuriousdragon.smartindex.sync.fakes

import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.sync.SyncReason
import com.kuriousdragon.smartindex.sync.work.ArmPolicy
import com.kuriousdragon.smartindex.sync.work.SyncScheduler

class FakeSyncScheduler : SyncScheduler {
    val arms = mutableListOf<Pair<Set<Group>, ArmPolicy>>()
    val enqueued = mutableListOf<Pair<SyncReason, Boolean>>()
    var periodicCalls = 0

    override suspend fun enqueueSync(reason: SyncReason, expedited: Boolean) {
        enqueued += reason to expedited
    }

    override fun ensurePeriodic() {
        periodicCalls++
    }

    override fun armTriggers(groups: Set<Group>, policy: ArmPolicy) {
        arms += groups to policy
    }
}
