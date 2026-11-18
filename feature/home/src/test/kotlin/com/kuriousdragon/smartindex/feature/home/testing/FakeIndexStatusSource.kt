package com.kuriousdragon.smartindex.feature.home.testing

import com.kuriousdragon.smartindex.core.model.IndexStatus
import com.kuriousdragon.smartindex.core.model.IndexStatusSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class FakeIndexStatusSource(initial: IndexStatus = IndexStatus()) : IndexStatusSource {
    val mutable = MutableStateFlow(initial)
    override val status: StateFlow<IndexStatus> get() = mutable
}
