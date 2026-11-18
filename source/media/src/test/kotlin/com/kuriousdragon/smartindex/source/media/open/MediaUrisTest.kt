package com.kuriousdragon.smartindex.source.media.open

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaUrisTest {
    @Test
    fun `a media content uri is returned`() {
        val uri = MediaUris.parseMediaUri("content://media/external/images/media/42")
        assertThat(uri.toString()).isEqualTo("content://media/external/images/media/42")
    }

    @Test
    fun `other schemes are rejected`() {
        assertThat(MediaUris.parseMediaUri("file:///sdcard/a.jpg")).isNull()
        assertThat(MediaUris.parseMediaUri("https://media/external/images/media/1")).isNull()
    }

    @Test
    fun `other authorities are rejected`() {
        assertThat(MediaUris.parseMediaUri("content://com.android.contacts/contacts/1")).isNull()
        assertThat(MediaUris.parseMediaUri("content://media.evil/external/1")).isNull()
    }

    @Test
    fun `null, empty and malformed strings give null without throwing`() {
        assertThat(MediaUris.parseMediaUri(null)).isNull()
        assertThat(MediaUris.parseMediaUri("")).isNull()
        assertThat(MediaUris.parseMediaUri("not a uri")).isNull()
        assertThat(MediaUris.parseMediaUri("content:")).isNull()
        assertThat(MediaUris.parseMediaUri("://")).isNull()
    }
}
