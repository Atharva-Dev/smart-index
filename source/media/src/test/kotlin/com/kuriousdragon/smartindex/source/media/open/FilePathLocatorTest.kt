package com.kuriousdragon.smartindex.source.media.open

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class FilePathLocatorTest {
    private val sd = File("/storage/1A2B-3C4D")
    private val locator = FilePathLocator { volume -> if (volume == "1a2b-3c4d") sd else null }

    private fun row(
        data: String? = null,
        name: String? = "a.zip",
        path: String? = "Backups/",
        volume: String? = "1a2b-3c4d",
    ) = OpenRow(1L, name, null, path, volume, data, isPending = false, isTrashed = false)

    @Test
    fun `non-blank DATA wins`() {
        assertThat(locator.locate(row(data = "/storage/emulated/0/Download/x.pdf")))
            .isEqualTo(File("/storage/emulated/0/Download/x.pdf"))
    }

    @Test
    fun `blank DATA falls back to volume directory, normalised path and name`() {
        assertThat(locator.locate(row(data = "  "))).isEqualTo(File(sd, "Backups/a.zip"))
        assertThat(locator.locate(row(path = "\\Backups\\Old"))).isEqualTo(File(sd, "Backups/Old/a.zip"))
        assertThat(locator.locate(row(path = null))).isEqualTo(File(sd, "a.zip"))
    }

    @Test
    fun `null when neither DATA nor a usable volume and name`() {
        assertThat(locator.locate(row(volume = null))).isNull()
        assertThat(locator.locate(row(volume = " "))).isNull()
        assertThat(locator.locate(row(volume = "unknown"))).isNull()
        assertThat(locator.locate(row(name = null))).isNull()
        assertThat(locator.locate(row(name = ""))).isNull()
    }
}
