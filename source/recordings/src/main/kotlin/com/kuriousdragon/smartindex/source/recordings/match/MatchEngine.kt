package com.kuriousdragon.smartindex.source.recordings.match

import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.RecordingInputs
import com.kuriousdragon.smartindex.core.model.RecordingLink
import com.kuriousdragon.smartindex.source.recordings.diag.RecordingDiagnostics
import com.kuriousdragon.smartindex.source.recordings.parse.RecordingNameParser
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** The whole matching algorithm (LLD §5.1). Pure: no I/O, nothing logged except via [diagnostics]. */
internal class MatchEngine(
    private val parser: RecordingNameParser,
    private val identities: IdentityMatcher,
    private val diagnostics: RecordingDiagnostics,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val generator = CandidateGenerator(identities)

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    suspend fun run(inputs: RecordingInputs, zone: ZoneId): MatchResult {
        val t0 = nanoTime()

        // 1. dedupe by id
        val calls = inputs.calls.distinctBy { it.id }
        val recordings = inputs.recordings.distinctBy { it.id }

        // 2. frozen links and existing links
        val callIds = HashSet<Long>(calls.size * 2)
        calls.forEach { callIds += it.id }
        val recIds = HashSet<Long>(recordings.size * 2)
        recordings.forEach { recIds += it.id }
        val frozenCalls = HashSet<Long>()
        val frozenRecs = HashSet<Long>()
        val existingByCall = HashMap<Long, Long>()
        var frozenLinks = 0
        val orderedLinks = inputs.links.sortedWith(compareBy({ it.callItemId }, { it.recordingItemId }))
        for (l in orderedLinks) {
            val callIn = l.callItemId in callIds
            val recIn = l.recordingItemId in recIds
            if (callIn && recIn) {
                existingByCall[l.callItemId] = l.recordingItemId
            } else {
                frozenLinks++
                if (callIn) frozenCalls += l.callItemId
                if (recIn) frozenRecs += l.recordingItemId
            }
        }

        // 3. eligible, non-frozen calls -> timeline
        val prepared = calls
            .filter { it.id !in frozenCalls && isEligible(it) }
            .map { PreparedCall(it, identities.ofCall(it)) }
            .sortedWith(compareBy({ it.call.primaryTs }, { it.call.id }))
        val timeline = CallTimeline(prepared)

        // 4. recordings
        var unparsed = 0
        var emptyFiles = 0
        var noAnchor = 0
        var noCandidate = 0
        var contradictions = 0
        val parsedByPattern = LinkedHashMap<String, Int>()
        val all = ArrayList<Candidate>()
        val withCandidates = HashSet<Long>()
        val sortedRecordings = recordings.filter { it.id !in frozenRecs }.sortedBy { it.id }
        for ((index, rec) in sortedRecordings.withIndex()) {
            if (index % MatcherConfig.CANCEL_CHECK_EVERY == 0) currentCoroutineContext().ensureActive()
            if (rec.sizeBytes == 0L) {
                emptyFiles++
                continue
            }
            try {
                val parsed = parser.parse(rec.title)
                if (parsed == null) {
                    unparsed++
                    diagnostics.onUnparsed(rec.title)
                } else {
                    parsedByPattern.merge(parsed.patternId, 1, Int::plus)
                }
                val anchors = AnchorResolver.anchors(rec, parsed, zone)
                if (anchors.isEmpty()) {
                    noAnchor++
                    continue
                }
                val pr = PreparedRecording(rec, parsed, identities.ofRecording(parsed), anchors)
                val generated = generator.candidates(pr, timeline, existingByCall)
                contradictions += generated.contradictions
                if (generated.candidates.isEmpty()) {
                    noCandidate++
                } else {
                    all += generated.candidates
                    withCandidates += rec.id
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                noAnchor++
            }
        }

        // 5. assignment
        currentCoroutineContext().ensureActive()
        val assigned = GreedyAssigner.assign(all)
        val assignedRecs = HashSet<Long>()
        assigned.forEach { assignedRecs += it.recordingId }
        val lost = withCandidates.count { it !in assignedRecs }

        // 6. links + stats
        val links = assigned.sortedBy { it.callId }.map {
            RecordingLink(it.callId, it.recordingId, it.scoreBp / SCORE_SCALE, it.method)
        }
        val byMethod = LinkedHashMap<String, Int>()
        assigned.forEach { byMethod.merge(it.method, 1, Int::plus) }
        val stats = MatchStats(
            calls = calls.size,
            eligibleCalls = timeline.size,
            recordings = recordings.size,
            parsedByPattern = parsedByPattern,
            unparsed = unparsed,
            emptyFiles = emptyFiles,
            noAnchor = noAnchor,
            frozenLinks = frozenLinks,
            candidates = all.size,
            contradictions = contradictions,
            linked = links.size,
            linkedByMethod = byMethod,
            unlinkedNoCandidate = noCandidate,
            unlinkedLost = lost,
            tookMs = (nanoTime() - t0) / NANOS_PER_MS,
        )
        diagnostics.onRunFinished(stats)
        return MatchResult(links, stats)
    }

    private fun isEligible(call: RecordingInputs.Call): Boolean {
        val d = call.durationMs
        if (d == null || d <= 0) return false
        return call.direction != Direction.MISSED &&
            call.direction != Direction.REJECTED &&
            call.direction != Direction.BLOCKED
    }

    private companion object {
        const val SCORE_SCALE = 10_000f
        const val NANOS_PER_MS = 1_000_000L
    }
}
