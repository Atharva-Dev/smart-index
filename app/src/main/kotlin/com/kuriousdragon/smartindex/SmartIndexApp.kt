package com.kuriousdragon.smartindex

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.kuriousdragon.smartindex.core.model.ThumbnailLoader
import com.kuriousdragon.smartindex.source.media.thumb.MediaImageLoader
import com.kuriousdragon.smartindex.sync.SyncStarter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * The Hilt application (LLD — App shell §3.4). Interfaces are added by the tickets that own them, each
 * without touching the others' members:
 * - `androidx.work.Configuration.Provider` + `SyncStarter.start()` in `onCreate`: SIDX-49 (LLD SIDX-12 §6.5);
 * - `coil3.SingletonImageLoader.Factory` (`newImageLoader`): SIDX-60 (LLD SIDX-8 §6.4);
 * - debug-only StrictMode in `onCreate`: the release-hardening ticket of LLD — App shell §6.4.
 */
@HiltAndroidApp
class SmartIndexApp : Application(), SingletonImageLoader.Factory, Configuration.Provider {
    @Inject lateinit var thumbnailLoader: ThumbnailLoader

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var syncStarter: SyncStarter

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.WARN)
            .build()

    override fun onCreate() {
        if (BuildConfig.DEBUG) StrictModeSetup.install()
        super.onCreate()
        syncStarter.start()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        MediaImageLoader.create(context, thumbnailLoader)
}
