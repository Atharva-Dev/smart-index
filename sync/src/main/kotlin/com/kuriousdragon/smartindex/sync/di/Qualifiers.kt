package com.kuriousdragon.smartindex.sync.di

import javax.inject.Qualifier

/** The process-wide scope of the orchestration (status collectors, catch-up timer). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SyncScope

/** The dispatcher the source indexers run on. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SyncIo
