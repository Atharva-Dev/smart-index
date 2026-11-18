package com.kuriousdragon.smartindex.feature.onboarding.testing

import com.kuriousdragon.smartindex.core.model.IndexControl

/** [IndexControl] that counts calls. */
class FakeIndexControl : IndexControl {
    var catchUpCount = 0
        private set
    var reindexCount = 0
        private set
    var permissionsChangedCount = 0
        private set

    override fun catchUpNow() {
        catchUpCount++
    }

    override fun reindexAll() {
        reindexCount++
    }

    override fun onPermissionsChanged() {
        permissionsChangedCount++
    }
}
