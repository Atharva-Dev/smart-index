package com.kuriousdragon.smartindex.source.media.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaNamesTest {
    @Test
    fun `title of null or blank is Unnamed file`() {
        assertThat(MediaNames.title(null)).isEqualTo("Unnamed file")
        assertThat(MediaNames.title("")).isEqualTo("Unnamed file")
        assertThat(MediaNames.title("   ")).isEqualTo("Unnamed file")
    }

    @Test
    fun `title is the trimmed display name`() {
        assertThat(MediaNames.title("  IMG 1.jpg ")).isEqualTo("IMG 1.jpg")
    }

    @Test
    fun `extension is lower-cased`() {
        assertThat(MediaNames.extension("a.PDF")).isEqualTo("pdf")
    }

    @Test
    fun `extension is null for dot files, no dot, trailing dot and null`() {
        assertThat(MediaNames.extension(".nomedia")).isNull()
        assertThat(MediaNames.extension("README")).isNull()
        assertThat(MediaNames.extension("a.")).isNull()
        assertThat(MediaNames.extension(null)).isNull()
    }

    @Test
    fun `extension of a tar gz is gz`() {
        assertThat(MediaNames.extension("a.tar.gz")).isEqualTo("gz")
    }

    @Test
    fun `extension longer than 10 characters is null and exactly 10 is kept`() {
        assertThat(MediaNames.extension("a.abcdefghijk")).isNull()
        assertThat(MediaNames.extension("a.abcdefghij")).isEqualTo("abcdefghij")
    }

    @Test
    fun `extension containing whitespace is null`() {
        assertThat(MediaNames.extension("my file.a b")).isNull()
    }

    @Test
    fun `folder is the last path segment`() {
        assertThat(MediaNames.folder("DCIM/Camera/", isRecording = false)).isEqualTo("Camera")
        assertThat(MediaNames.folder("Camera", isRecording = false)).isEqualTo("Camera")
        assertThat(MediaNames.folder("/DCIM/Camera", isRecording = false)).isEqualTo("Camera")
    }

    @Test
    fun `folder is null for root, empty and null paths`() {
        assertThat(MediaNames.folder("/", isRecording = false)).isNull()
        assertThat(MediaNames.folder("", isRecording = false)).isNull()
        assertThat(MediaNames.folder(null, isRecording = false)).isNull()
    }

    @Test
    fun `recording folder is the whole path without trailing slash`() {
        assertThat(MediaNames.folder("Recordings/Call/", isRecording = true)).isEqualTo("Recordings/Call")
        assertThat(MediaNames.folder("Call/", isRecording = true)).isEqualTo("Call")
    }

    @Test
    fun `normalisedPath handles backslashes and slashes`() {
        assertThat(MediaNames.normalisedPath("\\Recordings\\Call")).isEqualTo("Recordings/Call/")
        assertThat(MediaNames.normalisedPath("//a//")).isEqualTo("a/")
        assertThat(MediaNames.normalisedPath(null)).isEmpty()
        assertThat(MediaNames.normalisedPath("/")).isEmpty()
    }
}
