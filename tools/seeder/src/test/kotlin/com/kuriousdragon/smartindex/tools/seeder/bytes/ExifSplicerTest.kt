package com.kuriousdragon.smartindex.tools.seeder.bytes

import androidx.exifinterface.media.ExifInterface
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExifSplicerTest {
    private fun jpeg(withApp1: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        if (withApp1) out.write(byteArrayOf(0xFF.toByte(), 0xE1.toByte(), 0, 8, 'X'.code.toByte(), 0, 0, 0, 0, 0))
        // APP0 JFIF, minimal
        out.write(byteArrayOf(0xFF.toByte(), 0xE0.toByte(), 0, 16))
        out.write("JFIF".toByteArray() + byteArrayOf(0, 1, 1, 0, 0, 1, 0, 1, 0, 0))
        out.write(byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0, 2, 1, 2, 3, 0xFF.toByte(), 0xD9.toByte()))
        return out.toByteArray()
    }

    private fun app1Count(b: ByteArray): Int {
        var n = 0
        var pos = 2
        while (pos + 4 <= b.size && (b[pos].toInt() and 0xFF) == 0xFF) {
            val marker = b[pos + 1].toInt() and 0xFF
            if (marker == 0xDA) break
            if (marker == 0xE1) n++
            pos += 2 + ((b[pos + 2].toInt() and 0xFF) shl 8 or (b[pos + 3].toInt() and 0xFF))
        }
        return n
    }

    @Test
    fun writesDateTimeOriginalAndOffset() {
        val out = ExifSplicer.splice(
            jpeg(false),
            LocalDateTime.of(2026, 3, 9, 14, 5, 7),
            ZoneOffset.ofHoursMinutes(5, 30),
        )
        val exif = ExifInterface(ByteArrayInputStream(out))
        assertThat(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)).isEqualTo("2026:03:09 14:05:07")
        assertThat(exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)).isEqualTo("+05:30")
        assertThat(exif.getAttribute(ExifInterface.TAG_DATETIME)).isEqualTo("2026:03:09 14:05:07")
    }

    @Test
    fun soiFirstExactlyOneApp1AndExistingApp1Dropped() {
        val out = ExifSplicer.splice(jpeg(true), LocalDateTime.of(2020, 1, 2, 3, 4, 5), ZoneOffset.UTC)
        assertThat(out[0]).isEqualTo(0xFF.toByte())
        assertThat(out[1]).isEqualTo(0xD8.toByte())
        assertThat(app1Count(out)).isEqualTo(1)
        assertThat(String(out, 6, 4, Charsets.US_ASCII)).isEqualTo("Exif")
        assertThat(out.takeLast(2)).containsExactly(0xFF.toByte(), 0xD9.toByte()).inOrder()
        val exif = ExifInterface(ByteArrayInputStream(out))
        assertThat(exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)).isEqualTo("+00:00")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonJpeg() {
        ExifSplicer.splice(byteArrayOf(1, 2, 3, 4), LocalDateTime.now(), ZoneOffset.UTC)
    }
}
