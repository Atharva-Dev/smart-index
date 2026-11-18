package com.kuriousdragon.smartindex.source.recordings.match

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class PairScore(val scoreBp: Int, val method: String)

object RecordingScorer {
    /** Time score for anchor minus call start; null = outside the window (LLD §4.5). */
    fun timeScore(deltaMs: Long): Double? =
        when {
            deltaMs < -MatcherConfig.WINDOW_BEFORE_MS || deltaMs > MatcherConfig.WINDOW_AFTER_MS -> null

            deltaMs < 0 -> 1.0 + deltaMs.toDouble() / MatcherConfig.WINDOW_BEFORE_MS

            deltaMs <= MatcherConfig.PLATEAU_END_MS -> 1.0

            else ->
                1.0 - (deltaMs - MatcherConfig.PLATEAU_END_MS).toDouble() /
                    (MatcherConfig.WINDOW_AFTER_MS - MatcherConfig.PLATEAU_END_MS)
        }

    fun durationTolerance(callDurationMs: Long): Long =
        max(
            MatcherConfig.DURATION_TOL_MIN_MS,
            (callDurationMs * MatcherConfig.DURATION_TOL_FRACTION).roundToLong(),
        )

    /** Null when any gate fails (LLD §4.5). */
    fun score(
        deltaMs: Long,
        recordingDurationMs: Long?,
        callDurationMs: Long,
        identity: Identity,
        tier: AnchorTier,
    ): PairScore? {
        val sT = timeScore(deltaMs) ?: return null
        if (identity.contradicts) return null
        val sD: Double
        if (recordingDurationMs != null && recordingDurationMs > 0) {
            val tol = durationTolerance(callDurationMs)
            val diff = abs(recordingDurationMs - callDurationMs)
            if (diff > tol) return null
            sD = 1.0 - diff.toDouble() / tol
        } else {
            if (!identity.isMatch) return null
            sD = 0.0
        }
        val score =
            MatcherConfig.W_TIME * sT + MatcherConfig.W_DURATION * sD +
                MatcherConfig.W_IDENTITY * identity.score + MatcherConfig.W_ANCHOR * tier.score
        val bp = (score * 10_000).roundToInt()
        if (bp < MatcherConfig.MIN_SCORE_BP) return null
        return PairScore(bp, "${identity.label}+${tier.label}")
    }
}
