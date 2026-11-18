package com.kuriousdragon.smartindex.source.recordings.match

/** Constants of the recording matcher (LLD — Call-recording matcher §3.5). */
object MatcherConfig {
    /** Recording may start up to 10 s before the call's start time. */
    const val WINDOW_BEFORE_MS = 10_000L

    /** ... and up to 90 s after it (ring allowance). */
    const val WINDOW_AFTER_MS = 90_000L

    /** Delta in [0, 20 s] = full time score. */
    const val PLATEAU_END_MS = 20_000L

    /** Duration tolerance: +-5 s ... */
    const val DURATION_TOL_MIN_MS = 5_000L

    /** ... or +-5 % of the call duration, whichever is larger. */
    const val DURATION_TOL_FRACTION = 0.05
    const val W_TIME = 0.35
    const val W_DURATION = 0.30
    const val W_IDENTITY = 0.25
    const val W_ANCHOR = 0.10

    /** 0.50. */
    const val MIN_SCORE_BP = 5_000

    /** Recordings between ensureActive() checks. */
    const val CANCEL_CHECK_EVERY = 256
}
