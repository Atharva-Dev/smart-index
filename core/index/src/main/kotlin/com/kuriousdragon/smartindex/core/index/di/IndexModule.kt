package com.kuriousdragon.smartindex.core.index.di

import android.content.Context
import com.kuriousdragon.smartindex.core.index.db.IndexDatabase
import com.kuriousdragon.smartindex.core.index.query.RoomIndexQuery
import com.kuriousdragon.smartindex.core.index.query.SystemTimeZoneSource
import com.kuriousdragon.smartindex.core.index.query.TimeZoneSource
import com.kuriousdragon.smartindex.core.index.write.RoomIndexWriter
import com.kuriousdragon.smartindex.core.model.IndexQuery
import com.kuriousdragon.smartindex.core.model.IndexWriter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings of `:core:index` (LLD §8). `PhoneNumberNormalizer` is injected here but deliberately NOT
 * bound: the telephony module binds it once, and a second binding would fail the Hilt graph.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class IndexModule {
    @Binds
    abstract fun writer(impl: RoomIndexWriter): IndexWriter

    @Binds
    abstract fun query(impl: RoomIndexQuery): IndexQuery

    @Binds
    abstract fun timeZones(impl: SystemTimeZoneSource): TimeZoneSource

    companion object {
        @Provides
        @Singleton
        fun db(@ApplicationContext c: Context): IndexDatabase = IndexDatabase.build(c)
    }
}
