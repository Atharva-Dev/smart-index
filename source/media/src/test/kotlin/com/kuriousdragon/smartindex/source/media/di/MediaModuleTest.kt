package com.kuriousdragon.smartindex.source.media.di

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.ItemUriResolver
import com.kuriousdragon.smartindex.core.model.SourceIndexer
import com.kuriousdragon.smartindex.core.model.ThumbnailLoader
import com.kuriousdragon.smartindex.source.media.MediaStoreIndexer
import com.kuriousdragon.smartindex.source.media.open.DefaultItemUriResolver
import com.kuriousdragon.smartindex.source.media.open.FileProviderUris
import com.kuriousdragon.smartindex.source.media.open.FileUriFactory
import com.kuriousdragon.smartindex.source.media.run.MediaClock
import com.kuriousdragon.smartindex.source.media.run.SystemMediaClock
import com.kuriousdragon.smartindex.source.media.store.ContentResolverMediaStoreReader
import com.kuriousdragon.smartindex.source.media.store.MediaStoreReader
import com.kuriousdragon.smartindex.source.media.thumb.ContentResolverThumbnails
import com.kuriousdragon.smartindex.source.media.thumb.MediaStoreThumbnailLoader
import com.kuriousdragon.smartindex.source.media.thumb.SystemThumbnails
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Pins the bindings of [MediaModule] (LLD §3.3.7) without reflection. The anonymous subclass below must
 * override every `@Binds` method with the exact signature, so removing or changing a binding stops this file
 * from compiling; the companion providers are instantiated and their results asserted.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34])
class MediaModuleTest {
    @Suppress("unused")
    private val bindingsWitness = object : MediaModule() {
        override fun indexer(impl: MediaStoreIndexer): SourceIndexer = impl
        override fun resolver(impl: DefaultItemUriResolver): ItemUriResolver = impl
        override fun thumbnails(impl: MediaStoreThumbnailLoader): ThumbnailLoader = impl
        override fun reader(impl: ContentResolverMediaStoreReader): MediaStoreReader = impl
        override fun systemThumbnails(impl: ContentResolverThumbnails): SystemThumbnails = impl
        override fun clock(impl: SystemMediaClock): MediaClock = impl
        override fun fileUris(impl: FileProviderUris): FileUriFactory = impl
    }

    @Test
    fun `the media io dispatcher is Dispatchers IO`() {
        assertThat(MediaModule.io()).isSameInstanceAs(Dispatchers.IO)
    }

    @Test
    fun `volume directories never throw and tolerate unknown volumes`() {
        val context: Application = ApplicationProvider.getApplicationContext()
        val volumes = MediaModule.volumes(context)
        assertThat(volumes.directory("no-such-volume")).isNull()
    }
}
