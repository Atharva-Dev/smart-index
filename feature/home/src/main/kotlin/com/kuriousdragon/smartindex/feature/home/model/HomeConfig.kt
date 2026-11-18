package com.kuriousdragon.smartindex.feature.home.model

import kotlin.time.Duration.Companion.days

/** Feature-private constants (LLD — Home & Search UI §6.1). */
object HomeConfig {
    const val MAX_QUERY_CHARS: Int = 200
    const val COUNTS_WAIT_MS: Long = 1_500L

    /** Safety cap for a 1-2 character query: counts wait for the first page, which is the normal exit. */
    const val SHORT_COUNTS_WAIT_MS: Long = 30_000L

    const val PLAYER_POLL_MS: Long = 250L
    const val SEEK_STEP_MS: Long = 10_000L
    val PERMISSION_SNOOZE_MS: Long = 7.days.inWholeMilliseconds
}
