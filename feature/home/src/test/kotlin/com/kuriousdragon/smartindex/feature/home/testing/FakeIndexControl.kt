package com.kuriousdragon.smartindex.feature.home.testing

import com.kuriousdragon.smartindex.core.model.IndexControl

/** [IndexControl] that counts calls. */
internal class FakeIndexControl : IndexControl {
    var catchUpNowCalls = 0
        private set

    override fun catchUpNow() {
        catchUpNowCalls++
    }

    override fun reindexAll() = Unit

    override fun onPermissionsChanged() = Unit
}
