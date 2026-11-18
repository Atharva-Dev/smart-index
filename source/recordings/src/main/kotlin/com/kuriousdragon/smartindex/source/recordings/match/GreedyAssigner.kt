package com.kuriousdragon.smartindex.source.recordings.match

/** One scored (call, recording) pair (LLD §3.5). */
data class Candidate(
    val callId: Long,
    val recordingId: Long,
    val scoreBp: Int,
    val method: String,
    val absDeltaMs: Long,
    val absDurationDiffMs: Long,
    val isCopy: Boolean,
    val existing: Boolean,
)

object GreedyAssigner {
    /** Best first (LLD §4.7). Total order: ties end with recordingId then callId. */
    val ORDER: Comparator<Candidate> =
        compareByDescending<Candidate> { it.scoreBp }
            .thenByDescending { it.existing }
            .thenBy { it.absDeltaMs }
            .thenBy { it.absDurationDiffMs }
            .thenBy { it.isCopy }
            .thenBy { it.recordingId }
            .thenBy { it.callId }

    /** One-to-one: walks [candidates] once in [ORDER], accepting a pair when both sides are still free. */
    fun assign(candidates: List<Candidate>): List<Candidate> {
        val takenCalls = HashSet<Long>()
        val takenRecordings = HashSet<Long>()
        val out = ArrayList<Candidate>()
        for (c in candidates.sortedWith(ORDER)) {
            if (c.callId in takenCalls || c.recordingId in takenRecordings) continue
            takenCalls += c.callId
            takenRecordings += c.recordingId
            out += c
        }
        return out
    }
}
