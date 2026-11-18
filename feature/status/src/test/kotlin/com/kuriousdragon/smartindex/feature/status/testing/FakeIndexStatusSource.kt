package com.kuriousdragon.smartindex.feature.status.testing

import com.kuriousdragon.smartindex.core.model.IndexStatus
import com.kuriousdragon.smartindex.core.model.IndexStatusSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** [IndexStatusSource] driven by the test. */
class FakeIndexStatusSource(initial: IndexStatus = indexStatus()) : IndexStatusSource {
    private val flow = MutableStateFlow(initial)

    override val status: StateFlow<IndexStatus> get() = flow

    fun emit(status: IndexStatus) {
        flow.value = status
    }
}
