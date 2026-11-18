package com.kuriousdragon.smartindex.source.recordings.diag

import com.kuriousdragon.smartindex.source.recordings.match.MatchStats

/** Debug-only window into a matcher run (LLD §6.3). Never log names, numbers or titles. */
interface RecordingDiagnostics {
    fun onUnparsed(fileName: String)

    fun onRunFinished(stats: MatchStats)

    object None : RecordingDiagnostics {
        override fun onUnparsed(fileName: String) = Unit

        override fun onRunFinished(stats: MatchStats) = Unit
    }
}
