package com.kuriousdragon.smartindex.feature.status.testing

import com.kuriousdragon.smartindex.core.model.IndexControl

/** [IndexControl] that counts calls. */
class FakeIndexControl : IndexControl {
    var catchUpNowCalls = 0
        private set
    var reindexAllCalls = 0
        private set
    var onPermissionsChangedCalls = 0
        private set

    override fun catchUpNow() {
        catchUpNowCalls++
    }

    override fun reindexAll() {
        reindexAllCalls++
    }

    override fun onPermissionsChanged() {
        onPermissionsChangedCalls++
    }
}
