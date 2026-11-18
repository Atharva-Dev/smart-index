package com.kuriousdragon.smartindex.tools.seeder.bytes

import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Puts one APP1 Exif segment right after SOI (LLD §4.7): big-endian TIFF, IFD0 {0x0132 DateTime, 0x8769 ExifIFD},
 * ExifIFD {0x9003 DateTimeOriginal, 0x9011 OffsetTimeOriginal}. Any existing APP1 is dropped. Pure Kotlin.
 */
object ExifSplicer {
    private val STAMP = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    private const val TYPE_ASCII = 2
    private const val TYPE_LONG = 4

    fun splice(jpeg: ByteArray, taken: LocalDateTime, offset: ZoneOffset): ByteArray {
        require(jpeg.size >= 4 && u8(jpeg, 0) == 0xFF && u8(jpeg, 1) == 0xD8) { "not a JPEG (no SOI)" }
        val app1 = app1(taken, offset)
        val out = ByteArrayOutputStream(jpeg.size + app1.size)
        out.write(jpeg, 0, 2)
        out.write(app1)
        var pos = 2
        while (pos + 4 <= jpeg.size) {
            require(u8(jpeg, pos) == 0xFF) { "bad JPEG marker at $pos" }
            val marker = u8(jpeg, pos + 1)
            if (marker == 0xDA) break // start of scan: the rest is entropy-coded data
            if (marker == 0xFF) {
                pos++ // fill byte
                continue
            }
            val length = (u8(jpeg, pos + 2) shl 8) or u8(jpeg, pos + 3)
            require(length >= 2 && pos + 2 + length <= jpeg.size) { "truncated JPEG segment" }
            if (marker != 0xE1) out.write(jpeg, pos, 2 + length)
            pos += 2 + length
        }
        out.write(jpeg, pos, jpeg.size - pos)
        return out.toByteArray()
    }

    private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF

    private fun app1(taken: LocalDateTime, offset: ZoneOffset): ByteArray {
        val stamp = (STAMP.format(taken) + "\u0000").toByteArray(Charsets.US_ASCII) // 20 bytes
        val off = (offsetText(offset) + "\u0000").toByteArray(Charsets.US_ASCII) // 7 bytes
        val ifd0 = 8
        val exifIfd = ifd0 + 2 + 2 * 12 + 4
        val dataStart = exifIfd + 2 + 2 * 12 + 4
        val dateTimeAt = dataStart
        val originalAt = dateTimeAt + stamp.size
        val offsetAt = originalAt + stamp.size
        val tiff = ByteArrayOutputStream()

        fun u16(v: Int) {
            tiff.write(v ushr 8)
            tiff.write(v)
        }

        fun u32(v: Int) {
            u16(v ushr 16)
            u16(v and 0xFFFF)
        }

        fun entry(tag: Int, type: Int, count: Int, value: Int) {
            u16(tag)
            u16(type)
            u32(count)
            u32(value)
        }
        tiff.write("MM".toByteArray(Charsets.US_ASCII))
        u16(0x002A)
        u32(ifd0)
        u16(2)
        entry(0x0132, TYPE_ASCII, stamp.size, dateTimeAt)
        entry(0x8769, TYPE_LONG, 1, exifIfd)
        u32(0)
        u16(2)
        entry(0x9003, TYPE_ASCII, stamp.size, originalAt)
        entry(0x9011, TYPE_ASCII, off.size, offsetAt)
        u32(0)
        tiff.write(stamp)
        tiff.write(stamp)
        tiff.write(off)
        if (tiff.size() % 2 != 0) tiff.write(0)
        val body = tiff.toByteArray()
        val length = 2 + 6 + body.size
        val seg = ByteArrayOutputStream()
        seg.write(0xFF)
        seg.write(0xE1)
        seg.write(length ushr 8)
        seg.write(length)
        seg.write("Exif\u0000\u0000".toByteArray(Charsets.US_ASCII))
        seg.write(body)
        return seg.toByteArray()
    }

    /** `+05:30` (the Z of UTC becomes `+00:00`). */
    internal fun offsetText(offset: ZoneOffset): String {
        val total = offset.totalSeconds
        val sign = if (total < 0) '-' else '+'
        val abs = kotlin.math.abs(total)
        return "%c%02d:%02d".format(sign, abs / 3600, abs % 3600 / 60)
    }
}
