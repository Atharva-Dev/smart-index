package com.kuriousdragon.smartindex.source.recordings.match

import com.kuriousdragon.smartindex.core.model.RecordingInputs
import com.kuriousdragon.smartindex.source.recordings.parse.ParsedRecordingName
import kotlin.math.abs

internal data class PreparedRecording(
    val recording: RecordingInputs.Recording,
    val parsed: ParsedRecordingName?,
    val identity: RecordingIdentity,
    val anchors: List<Anchor>,
)

internal class CandidateGenerator(private val identities: IdentityMatcher) {
    /**
     * Candidates of one recording from the first anchor tier that yields any (LLD §4.6).
     * [existingByCall] maps call id to the recording id it is linked to now (rename tolerance).
     */
    fun candidates(rec: PreparedRecording, timeline: CallTimeline, existingByCall: Map<Long, Long>): Generated {
        var contradictions = 0
        val recId = rec.recording.id
        val recDuration = rec.recording.durationMs
        val isCopy = rec.parsed?.isCopy ?: false
        for (tier in AnchorTier.entries) {
            var best: HashMap<Long, Candidate>? = null
            for (anchor in rec.anchors) {
                if (anchor.tier != tier) continue
                val a = anchor.atMs
                timeline.forEachInRange(
                    a - MatcherConfig.WINDOW_AFTER_MS,
                    a + MatcherConfig.WINDOW_BEFORE_MS,
                ) { prepared ->
                    val call = prepared.call
                    val existing = existingByCall[call.id] == recId
                    var identity = identities.compare(rec.identity, prepared.identity)
                    if (identity == Identity.CONTRADICT_NAME && existing) identity = Identity.UNKNOWN
                    if (identity.contradicts) {
                        contradictions++
                        return@forEachInRange
                    }
                    val delta = a - call.primaryTs
                    val callDuration = call.durationMs ?: return@forEachInRange
                    val score = RecordingScorer.score(delta, recDuration, callDuration, identity, tier)
                        ?: return@forEachInRange
                    val candidate = Candidate(
                        callId = call.id,
                        recordingId = recId,
                        scoreBp = score.scoreBp,
                        method = score.method,
                        absDeltaMs = abs(delta),
                        absDurationDiffMs =
                            if (recDuration != null &&
                                recDuration > 0
                            ) {
                                abs(recDuration - callDuration)
                            } else {
                                UNKNOWN_DIFF
                            },
                        isCopy = isCopy,
                        existing = existing,
                    )
                    val map = best ?: HashMap<Long, Candidate>().also { best = it }
                    val old = map[call.id]
                    if (old == null || GreedyAssigner.ORDER.compare(candidate, old) < 0) map[call.id] = candidate
                }
            }
            val found = best
            if (found != null && found.isNotEmpty()) return Generated(found.values.toList(), contradictions)
        }
        return Generated(emptyList(), contradictions)
    }

    data class Generated(val candidates: List<Candidate>, val contradictions: Int)

    private companion object {
        const val UNKNOWN_DIFF = Long.MAX_VALUE
    }
}
