package com.kuriousdragon.smartindex.source.media.open

import android.webkit.MimeTypeMap
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.ItemType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MimeResolverTest {
    init {
        shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("pdf", "application/pdf")
    }

    @Test
    fun `row mime is normalised and wins`() {
        assertThat(MimeResolver.mimeFor(" Image/JPEG ", "x.pdf", ItemType.PHOTO)).isEqualTo("image/jpeg")
    }

    @Test
    fun `octet-stream or blank row mime falls back to the extension`() {
        assertThat(MimeResolver.mimeFor("application/octet-stream", "a.PDF", ItemType.DOCUMENT))
            .isEqualTo("application/pdf")
        assertThat(MimeResolver.mimeFor("  ", "a.pdf", ItemType.DOCUMENT)).isEqualTo("application/pdf")
        assertThat(MimeResolver.mimeFor(null, "a.pdf", ItemType.DOCUMENT)).isEqualTo("application/pdf")
    }

    @Test
    fun `unknown extension keeps a non-blank octet-stream row mime`() {
        assertThat(MimeResolver.mimeFor("application/octet-stream", "a.zzzunknown", ItemType.OTHER_FILE))
            .isEqualTo("application/octet-stream")
    }

    @Test
    fun `no mime and no known extension falls back by type`() {
        assertThat(MimeResolver.mimeFor(null, "IMG", ItemType.PHOTO)).isEqualTo("image/*")
        assertThat(MimeResolver.mimeFor(null, "clip", ItemType.VIDEO)).isEqualTo("video/*")
        assertThat(MimeResolver.mimeFor(null, "rec", ItemType.RECORDING)).isEqualTo("audio/*")
        assertThat(MimeResolver.mimeFor(null, "x.zzzunknown", ItemType.DOCUMENT)).isEqualTo("application/octet-stream")
        assertThat(MimeResolver.mimeFor("", "x", ItemType.OTHER_FILE)).isEqualTo("application/octet-stream")
    }
}
