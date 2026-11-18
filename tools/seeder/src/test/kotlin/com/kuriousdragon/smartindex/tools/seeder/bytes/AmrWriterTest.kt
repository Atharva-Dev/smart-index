package com.kuriousdragon.smartindex.tools.seeder.bytes

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AmrWriterTest {
    @Test
    fun sizeIsHeaderPlusFrames() {
        for (ms in listOf(0L, 1L, 20L, 21L, 1_000L, 65_432L)) {
            val frames = (ms + 19) / 20
            assertThat(AmrWriter.silence(ms).size.toLong()).isEqualTo(6 + 32 * frames)
        }
    }

    @Test
    fun headerAndFrameLayout() {
        val b = AmrWriter.silence(60)
        assertThat(String(b, 0, 6, Charsets.US_ASCII)).isEqualTo("#!AMR\n")
        for (i in 0 until 3) {
            assertThat(b[6 + i * 32]).isEqualTo(0x3C.toByte())
            assertThat(b.slice(7 + i * 32 until 6 + (i + 1) * 32).all { it == 0.toByte() }).isTrue()
        }
    }
}
