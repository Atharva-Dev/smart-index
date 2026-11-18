package com.kuriousdragon.smartindex.core.model

/**
 * Compile-time configuration (HLD §7 "Configuration"). There is no runtime or remote config.
 * Owner: "LLD — Indexing orchestration & scheduling" (SIDX-12). Other LLDs read these values and add
 * new constants here only through a change to that LLD.
 */
object IndexConfig {
    // ---- Store and UI (HLD §6.2, §7 Performance, Design Spec §6.1, §6.9) ----

    /** Maximum [ItemRecord]s per `IndexWriter.upsert` call; one transaction each. */
    const val UPSERT_BATCH_SIZE: Int = 500

    /** Paging page size for `IndexQuery.timeline` and `IndexQuery.search`. */
    const val PAGE_SIZE: Int = 50

    /** Search-as-you-type debounce after the last keystroke, ms. */
    const val SEARCH_DEBOUNCE_MS: Long = 300L

    /**
     * Queries of at most this many code points use the substring scan; used by the store plan and by Home
     * counts.
     */
    const val SHORT_QUERY_MAX_CODE_POINTS: Int = 2

    /** Skeleton rows appear only when loading takes longer than this, ms. */
    const val SKELETON_DELAY_MS: Long = 300L

    /** Thumbnail edge for `ContentResolver.loadThumbnail`, px (48 dp row visual, HLD §4). */
    const val THUMBNAIL_SIZE_PX: Int = 144

    // ---- Scheduling (HLD §7.1 NFR-005, ADR-007) ----

    /** Periodic backstop interval, minutes (WorkManager minimum; NFR-005 freshness bound). */
    const val PERIODIC_INTERVAL_MINUTES: Long = 15L

    /** Flex window at the end of each periodic interval, minutes. */
    const val PERIODIC_FLEX_MINUTES: Long = 5L

    /** Content-URI trigger: run once no further change arrived for this long, ms. */
    const val CONTENT_TRIGGER_UPDATE_DELAY_MS: Long = 10_000L

    /** Content-URI trigger: run at the latest this long after the first change, ms. */
    const val CONTENT_TRIGGER_MAX_DELAY_MS: Long = 60_000L

    /** In-process ContentObserver debounce while the app is in the foreground, ms. */
    const val FOREGROUND_CHANGE_DEBOUNCE_MS: Long = 2_000L

    // ---- Orchestration (HLD §7.1 NFR-004, NFR-010) ----

    /** Source indexers that run at the same time (≤ 2 IO workers). */
    const val MAX_PARALLEL_INDEXERS: Int = 2

    /** A silent catch-up shows the thin progress bar only after running this long, ms (Design Spec §6.8). */
    const val CATCH_UP_PROGRESS_DELAY_MS: Long = 2_000L

    /** A source whose last FULL is older than this runs FULL again (safety net for missed edits), ms. */
    const val FULL_RESYNC_INTERVAL_MS: Long = 7L * 24 * 60 * 60 * 1000

    /** Incremental recording matching looks back this far before the previous match run, ms. */
    const val RECORDING_MATCH_OVERLAP_MS: Long = 48L * 60 * 60 * 1000

    /** Below this much free space in app storage a run is skipped with STORAGE_FULL, bytes. */
    const val MIN_FREE_BYTES: Long = 50L * 1024 * 1024
}
