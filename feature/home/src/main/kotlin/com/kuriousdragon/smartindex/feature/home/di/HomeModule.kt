package com.kuriousdragon.smartindex.feature.home.di

import com.kuriousdragon.smartindex.feature.home.action.ContactLookup
import com.kuriousdragon.smartindex.feature.home.action.ContentResolverContactLookup
import com.kuriousdragon.smartindex.feature.home.model.DefaultDeviceEnvSource
import com.kuriousdragon.smartindex.feature.home.model.DeviceEnvSource
import com.kuriousdragon.smartindex.feature.home.model.HomeClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Dispatchers.Default. */
@Qualifier
annotation class HomeDefault

/** Dispatchers.IO. */
@Qualifier
annotation class HomeIo

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeModule {
    @Binds
    abstract fun env(impl: DefaultDeviceEnvSource): DeviceEnvSource

    @Binds
    abstract fun contacts(impl: ContentResolverContactLookup): ContactLookup

    companion object {
        @Provides
        fun clock(): HomeClock = HomeClock { System.currentTimeMillis() }

        @Provides
        @HomeDefault
        fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

        @Provides
        @HomeIo
        fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
