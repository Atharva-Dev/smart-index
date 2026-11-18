package com.kuriousdragon.smartindex.source.recordings.di

import com.kuriousdragon.smartindex.core.model.RecordingMatcher
import com.kuriousdragon.smartindex.source.recordings.BuildConfig
import com.kuriousdragon.smartindex.source.recordings.SamsungRecordingMatcher
import com.kuriousdragon.smartindex.source.recordings.ZoneIdProvider
import com.kuriousdragon.smartindex.source.recordings.diag.LogShapeDiagnostics
import com.kuriousdragon.smartindex.source.recordings.diag.RecordingDiagnostics
import com.kuriousdragon.smartindex.source.recordings.parse.RecordingNameParser
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Dispatcher the matcher runs on (CPU-bound; `Dispatchers.Default` in production). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RecordingsDispatcher

@Module
@InstallIn(SingletonComponent::class)
abstract class RecordingsModule {
    /** The single RecordingMatcher binding; `:sync` consumes it as `Optional<RecordingMatcher>` (@BindsOptionalOf). */
    @Binds
    @Singleton
    abstract fun recordingMatcher(impl: SamsungRecordingMatcher): RecordingMatcher

    companion object {
        @Provides fun zoneIdProvider(): ZoneIdProvider = ZoneIdProvider { ZoneId.systemDefault() }

        @Provides @Singleton
        fun recordingNameParser(): RecordingNameParser = RecordingNameParser()

        @Provides
        @Singleton
        fun recordingDiagnostics(): RecordingDiagnostics =
            if (BuildConfig.DEBUG) LogShapeDiagnostics() else RecordingDiagnostics.None

        @Provides @RecordingsDispatcher
        fun dispatcher(): CoroutineDispatcher = Dispatchers.Default
    }
}
