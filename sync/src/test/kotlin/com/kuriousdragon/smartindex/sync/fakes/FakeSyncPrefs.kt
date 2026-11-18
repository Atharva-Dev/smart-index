package com.kuriousdragon.smartindex.sync.fakes

import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.sync.SyncPrefs

class FakeSyncPrefs(
    override var enabled: Boolean = false,
    override var reindexRequested: Boolean = false,
    override var grantedGroups: Set<Group>? = null,
    override var lastMatchRun: Long? = null,
) : SyncPrefs
