package com.kuriousdragon.smartindex.source.recordings.diag

import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.source.recordings.match.MatchStats

/** Debug builds only (RecordingsModule). Collects masked shapes during one run and logs them at the end. */
class LogShapeDiagnostics(
    private val sink: (String) -> Unit = { SiLog.d(TAG, it) },
) : RecordingDiagnostics {
    private val shapes = LinkedHashMap<String, Int>()

    @Synchronized
    override fun onUnparsed(fileName: String) {
        val s = NameShape.mask(fileName)
        if (s in shapes || shapes.size < MAX_SHAPES) shapes[s] = (shapes[s] ?: 0) + 1
    }

    @Synchronized
    override fun onRunFinished(stats: MatchStats) {
        sink(
            "match calls=${stats.calls} eligible=${stats.eligibleCalls} recs=${stats.recordings} " +
                "parsed=${stats.parsedByPattern} unparsed=${stats.unparsed} empty=${stats.emptyFiles} " +
                "noAnchor=${stats.noAnchor} frozen=${stats.frozenLinks} cand=${stats.candidates} " +
                "contra=${stats.contradictions} linked=${stats.linked} byMethod=${stats.linkedByMethod} " +
                "noCand=${stats.unlinkedNoCandidate} lost=${stats.unlinkedLost} ms=${stats.tookMs}",
        )
        shapes.forEach { (s, c) -> sink("unparsed shape=\"$s\" count=$c") }
        shapes.clear()
    }

    companion object {
        const val TAG = "SiRecMatch"
        const val MAX_SHAPES = 50
    }
}
