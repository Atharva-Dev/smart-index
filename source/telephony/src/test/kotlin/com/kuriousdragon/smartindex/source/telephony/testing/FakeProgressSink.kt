package com.kuriousdragon.smartindex.source.telephony.testing

import com.kuriousdragon.smartindex.core.model.ProgressSink

/** Records every `report(done, total)`. */
class FakeProgressSink(private val onReport: (done: Int, total: Int) -> Unit = { _, _ -> }) : ProgressSink {
    val reports: MutableList<Pair<Int, Int>> = mutableListOf()

    override fun report(done: Int, total: Int) {
        reports += done to total
        onReport(done, total)
    }
}
