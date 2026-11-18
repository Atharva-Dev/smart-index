package com.kuriousdragon.smartindex.sync.di

import android.content.Context
import androidx.work.WorkManager
import com.kuriousdragon.smartindex.core.model.IndexControl
import com.kuriousdragon.smartindex.core.model.IndexStatusSource
import com.kuriousdragon.smartindex.core.model.RecordingMatcher
import com.kuriousdragon.smartindex.core.model.SourceIndexer
import com.kuriousdragon.smartindex.sync.AndroidFreeSpace
import com.kuriousdragon.smartindex.sync.DefaultIndexControl
import com.kuriousdragon.smartindex.sync.FreeSpace
import com.kuriousdragon.smartindex.sync.IndexCoordinator
import com.kuriousdragon.smartindex.sync.SharedPrefsSyncPrefs
import com.kuriousdragon.smartindex.sync.SyncClock
import com.kuriousdragon.smartindex.sync.SyncPrefs
import com.kuriousdragon.smartindex.sync.SyncRunner
import com.kuriousdragon.smartindex.sync.SystemSyncClock
import com.kuriousdragon.smartindex.sync.work.SyncScheduler
import com.kuriousdragon.smartindex.sync.work.WorkSyncScheduler
import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hilt bindings of the orchestration (LLD — Indexing orchestration §3.4). */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds abstract fun indexStatusSource(impl: IndexCoordinator): IndexStatusSource

    @Binds abstract fun syncRunner(impl: IndexCoordinator): SyncRunner

    @Binds abstract fun indexControl(impl: DefaultIndexControl): IndexControl

    @Binds abstract fun syncScheduler(impl: WorkSyncScheduler): SyncScheduler

    @Binds abstract fun syncPrefs(impl: SharedPrefsSyncPrefs): SyncPrefs

    @Binds abstract fun syncClock(impl: SystemSyncClock): SyncClock

    @Binds abstract fun freeSpace(impl: AndroidFreeSpace): FreeSpace

    /** Empty set is valid until the indexers land. */
    @Multibinds abstract fun sourceIndexers(): Set<SourceIndexer>

    /** Absent until the recording matcher lands. */
    @BindsOptionalOf abstract fun recordingMatcher(): RecordingMatcher

    companion object {
        @Provides
        @Singleton
        fun workManager(@ApplicationContext c: Context): WorkManager = WorkManager.getInstance(c)

        @Provides
        @Singleton
        @SyncScope
        fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Provides
        @SyncIo
        fun io(): CoroutineDispatcher = Dispatchers.IO
    }
}
