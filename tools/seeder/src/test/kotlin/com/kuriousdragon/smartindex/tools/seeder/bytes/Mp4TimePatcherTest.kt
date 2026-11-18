package com.kuriousdragon.smartindex.tools.seeder.bytes

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import org.junit.Test

class Mp4TimePatcherTest {
    private fun box(type: String, payload: ByteArray): ByteArray =
        ByteBuffer.allocate(8 + payload.size).putInt(8 + payload.size).put(type.toByteArray()).put(payload).array()

    private fun full(version: Int): ByteArray {
        val n = if (version == 0) 8 else 16
        return ByteArray(4 + n + 8).also { it[0] = version.toByte() }
    }

    private fun sample(version: Int): ByteArray {
        val mdhd = box("mdhd", full(version))
        val tkhd = box("tkhd", full(version))
        val trak = box("trak", tkhd + box("mdia", mdhd))
        val moov = box("moov", box("mvhd", full(version)) + trak)
        return box("ftyp", ByteArray(8)) + moov + box("mdat", ByteArray(4))
    }

    private fun times(b: ByteArray, type: String, v1: Boolean): Pair<Long, Long> {
        val idx = String(b, Charsets.ISO_8859_1).indexOf(type)
        val bb = ByteBuffer.wrap(b)
        val p = idx + 4 + 4
        return if (v1) {
            bb.getLong(p) to bb.getLong(p + 8)
        } else {
            (bb.getInt(p).toLong() and 0xFFFFFFFFL) to
                (bb.getInt(p + 4).toLong() and 0xFFFFFFFFL)
        }
    }

    @Test
    fun patchesVersion0() {
        val out = Mp4TimePatcher.patch(sample(0), 1_700_000_000L)
        val expected = 1_700_000_000L + 2_082_844_800L
        for (t in listOf("mvhd", "tkhd", "mdhd")) assertThat(times(out, t, false)).isEqualTo(expected to expected)
        assertThat(out.size).isEqualTo(sample(0).size)
    }

    @Test
    fun patchesVersion1() {
        val out = Mp4TimePatcher.patch(sample(1), 1_700_000_000L)
        val expected = 1_700_000_000L + 2_082_844_800L
        for (t in listOf("mvhd", "tkhd", "mdhd")) assertThat(times(out, t, true)).isEqualTo(expected to expected)
    }

    @Test
    fun doesNotMutateInput() {
        val input = sample(0)
        val copy = input.copyOf()
        Mp4TimePatcher.patch(input, 5L)
        assertThat(input).isEqualTo(copy)
    }

    @Test(expected = IllegalArgumentException::class)
    fun truncatedInputThrows() {
        val s = sample(0)
        Mp4TimePatcher.patch(s.copyOf(s.size - 20), 5L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun noHeadersThrows() {
        Mp4TimePatcher.patch(box("ftyp", ByteArray(8)), 5L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownVersionThrows() {
        val bad = box("moov", box("mvhd", ByteArray(24).also { it[0] = 7 }))
        Mp4TimePatcher.patch(bad, 5L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun garbageThrows() {
        Mp4TimePatcher.patch(ByteArrayOutputStream().apply { write(ByteArray(5)) }.toByteArray(), 5L)
    }
}
