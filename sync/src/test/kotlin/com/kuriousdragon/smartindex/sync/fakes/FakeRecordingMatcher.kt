package com.kuriousdragon.smartindex.sync.fakes

import com.kuriousdragon.smartindex.core.model.RecordingInputs
import com.kuriousdragon.smartindex.core.model.RecordingLink
import com.kuriousdragon.smartindex.core.model.RecordingMatcher

class FakeRecordingMatcher(
    var links: List<RecordingLink> = listOf(RecordingLink(1L, 2L, 1f, "time")),
    var throwable: Throwable? = null,
) : RecordingMatcher {
    val inputs = mutableListOf<RecordingInputs>()

    override suspend fun match(inputs: RecordingInputs): List<RecordingLink> {
        this.inputs += inputs
        throwable?.let { throw it }
        return links
    }
}
