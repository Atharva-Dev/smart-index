package com.kuriousdragon.smartindex.tools.seeder.bytes

/**
 * Rewrites creation and modification time in `mvhd`, `tkhd` and `mdhd` (seconds since 1904-01-01; 32-bit for
 * version 0, 64-bit for version 1; LLD §4.7). Walks `moov` → `mvhd`, `trak` → `tkhd`, `mdia` → `mdhd`.
 * Any unexpected layout throws [IllegalArgumentException]. Pure Kotlin.
 */
object Mp4TimePatcher {
    /** Seconds between 1904-01-01 and 1970-01-01. */
    const val EPOCH_1904_OFFSET = 2_082_844_800L
    private val CONTAINERS = setOf("moov", "trak", "mdia")
    private val HEADERS = setOf("mvhd", "tkhd", "mdhd")

    fun patch(mp4: ByteArray, epochSec: Long): ByteArray {
        val t1904 = epochSec + EPOCH_1904_OFFSET
        require(t1904 >= 0) { "time before 1904" }
        val out = mp4.copyOf()
        var patched = 0
        fun walk(start: Int, end: Int) {
            var pos = start
            while (pos < end) {
                require(end - pos >= 8) { "truncated box header" }
                var size = u32(out, pos)
                val type = String(out, pos + 4, 4, Charsets.ISO_8859_1)
                var header = 8
                if (size == 1L) {
                    require(end - pos >= 16) { "truncated largesize" }
                    size = u64(out, pos + 8)
                    header = 16
                } else if (size == 0L) {
                    size = (end - pos).toLong()
                }
                require(size >= header && size <= end - pos) { "box $type overruns its parent" }
                val boxEnd = pos + size.toInt()
                val payload = pos + header
                when (type) {
                    in CONTAINERS -> walk(payload, boxEnd)

                    in HEADERS -> {
                        patchHeader(out, payload, boxEnd, t1904, type)
                        patched++
                    }
                }
                pos = boxEnd
            }
        }
        walk(0, out.size)
        require(patched > 0) { "no mvhd/tkhd/mdhd found" }
        return out
    }

    private fun patchHeader(b: ByteArray, payload: Int, boxEnd: Int, t1904: Long, type: String) {
        require(boxEnd - payload >= 4) { "truncated $type" }
        when (val version = b[payload].toInt() and 0xFF) {
            0 -> {
                require(boxEnd - payload >= 12) { "truncated $type v0" }
                require(t1904 <= 0xFFFF_FFFFL) { "time too large for a version 0 $type" }
                put32(b, payload + 4, t1904)
                put32(b, payload + 8, t1904)
            }

            1 -> {
                require(boxEnd - payload >= 20) { "truncated $type v1" }
                put64(b, payload + 4, t1904)
                put64(b, payload + 12, t1904)
            }

            else -> throw IllegalArgumentException("unknown $type version $version")
        }
    }

    private fun u32(b: ByteArray, i: Int): Long =
        (0 until 4).fold(0L) { acc, k -> (acc shl 8) or (b[i + k].toLong() and 0xFF) }

    private fun u64(b: ByteArray, i: Int): Long =
        (0 until 8).fold(0L) { acc, k -> (acc shl 8) or (b[i + k].toLong() and 0xFF) }

    private fun put32(b: ByteArray, i: Int, v: Long) {
        for (k in 0 until 4) b[i + k] = (v ushr (8 * (3 - k))).toByte()
    }

    private fun put64(b: ByteArray, i: Int, v: Long) {
        for (k in 0 until 8) b[i + k] = (v ushr (8 * (7 - k))).toByte()
    }
}
