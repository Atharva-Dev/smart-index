package com.kuriousdragon.smartindex

import android.app.Application
import android.content.pm.PackageManager
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.source.media.open.FileProviderUris
import java.io.File
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The merged app manifest's FileProvider and `file_paths.xml` against the URIs of `FileProviderUris`
 * (LLD — Media & file indexer §6.4, ADR-012).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34])
class FileProviderPathsTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val uris = FileProviderUris(context)

    /** FileProvider caches its parsed paths per authority in a static map, but Robolectric's storage root is per test. */
    @Before
    fun resetFileProviderCache() {
        val cache = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        (cache.get(null) as MutableMap<*, *>).clear()
    }

    @Test
    fun `a file on primary storage maps to the shared root`() {
        val file = File(Environment.getExternalStorageDirectory(), "Download/a.pdf")

        val uri = uris.uriFor(file)

        assertThat(uri.toString()).isEqualTo("content://${context.packageName}.files/shared/Download/a.pdf")
    }

    @Test
    fun `a file outside every root is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            uris.uriFor(File("/data/local/tmp/outside.pdf"))
        }
    }

    @Test
    fun `the provider is not exported and grants URI permissions`() {
        val authority = "${context.packageName}.files"

        val info = context.packageManager.resolveContentProvider(authority, PackageManager.GET_META_DATA)

        assertThat(info).isNotNull()
        assertThat(info!!.exported).isFalse()
        assertThat(info.grantUriPermissions).isTrue()
    }
}
