package com.kuriousdragon.smartindex.source.media.di

import android.content.Context
import android.os.storage.StorageManager
import android.provider.MediaStore
import com.kuriousdragon.smartindex.core.model.ItemUriResolver
import com.kuriousdragon.smartindex.core.model.SourceIndexer
import com.kuriousdragon.smartindex.core.model.ThumbnailLoader
import com.kuriousdragon.smartindex.source.media.MediaStoreIndexer
import com.kuriousdragon.smartindex.source.media.open.DefaultItemUriResolver
import com.kuriousdragon.smartindex.source.media.open.FileProviderUris
import com.kuriousdragon.smartindex.source.media.open.FileUriFactory
import com.kuriousdragon.smartindex.source.media.open.VolumeDirectories
import com.kuriousdragon.smartindex.source.media.run.MediaClock
import com.kuriousdragon.smartindex.source.media.run.SystemMediaClock
import com.kuriousdragon.smartindex.source.media.store.ContentResolverMediaStoreReader
import com.kuriousdragon.smartindex.source.media.store.MediaStoreReader
import com.kuriousdragon.smartindex.source.media.thumb.ContentResolverThumbnails
import com.kuriousdragon.smartindex.source.media.thumb.MediaStoreThumbnailLoader
import com.kuriousdragon.smartindex.source.media.thumb.SystemThumbnails
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Hilt bindings of `:source:media` (LLD — Media & file indexer §3.3.7). The indexer joins `Set<SourceIndexer>`;
 * no `ContentResolver`, `Context` or unqualified `CoroutineDispatcher` is bound here (no duplicates with other modules).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MediaModule {
    @Binds @IntoSet
    abstract fun indexer(impl: MediaStoreIndexer): SourceIndexer

    @Binds @Singleton
    abstract fun resolver(impl: DefaultItemUriResolver): ItemUriResolver

    @Binds @Singleton
    abstract fun thumbnails(impl: MediaStoreThumbnailLoader): ThumbnailLoader

    @Binds
    abstract fun reader(impl: ContentResolverMediaStoreReader): MediaStoreReader

    @Binds
    abstract fun systemThumbnails(impl: ContentResolverThumbnails): SystemThumbnails

    @Binds
    abstract fun clock(impl: SystemMediaClock): MediaClock

    @Binds
    abstract fun fileUris(impl: FileProviderUris): FileUriFactory

    companion object {
        @Provides @MediaIo
        fun io(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        fun volumes(@ApplicationContext c: Context): VolumeDirectories = VolumeDirectories { v ->
            runCatching {
                c.getSystemService(StorageManager::class.java)
                    .getStorageVolume(MediaStore.Files.getContentUri(v))?.directory
            }.getOrNull()
        }
    }
}
