package com.kuriousdragon.smartindex.perf

import com.kuriousdragon.smartindex.core.model.IndexStatusSource
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Optional

/** Hilt access to the status source for [PerfMarkersInitializer] (LLD — Test & benchmark harness §3.2). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PerfEntryPoint {
    fun indexStatusSource(): Optional<IndexStatusSource>
}

/**
 * Declares the status source as optional so the graph builds before SyncModule's
 * `@Binds IndexStatusSource ← IndexCoordinator` (SIDX-49) lands; until then the Optional is empty.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PerfModule {
    @BindsOptionalOf
    abstract fun indexStatusSource(): IndexStatusSource
}
