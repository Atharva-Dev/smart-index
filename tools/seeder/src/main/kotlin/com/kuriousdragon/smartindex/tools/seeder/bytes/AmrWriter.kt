package com.kuriousdragon.smartindex.tools.seeder.bytes

/** AMR-NB file of silence: `#!AMR\n` + ceil(ms / 20) frames of 0x3C (12.2 kbps) + 31 zero bytes (LLD §4.7). */
object AmrWriter {
    private val HEADER = "#!AMR\n".toByteArray(Charsets.US_ASCII)
    private const val FRAME_BYTES = 32
    private const val FRAME_MS = 20L

    fun silence(durationMs: Long): ByteArray {
        require(durationMs >= 0) { "negative duration" }
        val frames = ((durationMs + FRAME_MS - 1) / FRAME_MS).toInt()
        val out = ByteArray(HEADER.size + frames * FRAME_BYTES)
        HEADER.copyInto(out)
        for (i in 0 until frames) out[HEADER.size + i * FRAME_BYTES] = 0x3C
        return out
    }
}
