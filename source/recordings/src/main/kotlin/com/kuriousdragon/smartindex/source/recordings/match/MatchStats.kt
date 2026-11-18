package com.kuriousdragon.smartindex.source.recordings.match

import com.kuriousdragon.smartindex.core.model.RecordingLink

/** Counts only, never content. Debug-log use only (LLD §4.9). */
data class MatchStats(
    val calls: Int,
    val eligibleCalls: Int,
    val recordings: Int,
    val parsedByPattern: Map<String, Int>,
    val unparsed: Int,
    val emptyFiles: Int,
    val noAnchor: Int,
    val frozenLinks: Int,
    val candidates: Int,
    val contradictions: Int,
    val linked: Int,
    val linkedByMethod: Map<String, Int>,
    val unlinkedNoCandidate: Int,
    val unlinkedLost: Int,
    val tookMs: Long,
)

data class MatchResult(val links: List<RecordingLink>, val stats: MatchStats)
