package com.kuriousdragon.smartindex.feature.player.di

import android.content.Context
import com.kuriousdragon.smartindex.feature.player.DefaultRecordingPlayer
import com.kuriousdragon.smartindex.feature.player.ExoPlaybackEngine
import com.kuriousdragon.smartindex.feature.player.PlaybackEngineFactory
import com.kuriousdragon.smartindex.feature.player.RecordingPlayer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Dispatchers.Main.immediate. */
@Qualifier
annotation class PlayerMain

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {
    @Binds
    @Singleton
    abstract fun player(impl: DefaultRecordingPlayer): RecordingPlayer

    companion object {
        @Provides
        fun engineFactory(@ApplicationContext context: Context): PlaybackEngineFactory =
            PlaybackEngineFactory { ExoPlaybackEngine(context) }

        @Provides
        @PlayerMain
        fun main(): CoroutineDispatcher = Dispatchers.Main.immediate
    }
}
