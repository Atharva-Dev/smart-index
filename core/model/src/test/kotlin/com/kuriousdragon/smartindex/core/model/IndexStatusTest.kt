package com.kuriousdragon.smartindex.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IndexStatusTest {
    @Test
    fun `percent is 0 when total is unknown`() {
        assertThat(IndexStatus(phase = IndexPhase.INDEXING, done = 50, total = 0).percent).isEqualTo(0)
    }

    @Test
    fun `percent is computed and clamped to 100`() {
        assertThat(IndexStatus(done = 50, total = 200).percent).isEqualTo(25)
        assertThat(IndexStatus(done = 300, total = 200).percent).isEqualTo(100)
    }

    @Test
    fun `percent does not overflow at 200k items`() {
        assertThat(IndexStatus(done = 200_000, total = 200_000).percent).isEqualTo(100)
        assertThat(IndexStatus(done = 100_000, total = 200_000).percent).isEqualTo(50)
        assertThat(IndexStatus(done = Int.MAX_VALUE, total = Int.MAX_VALUE).percent).isEqualTo(100)
    }

    @Test
    fun `is indexing only when running and FULL`() {
        assertThat(IndexStatus(phase = IndexPhase.INDEXING, mode = Mode.FULL).isIndexing).isTrue()
        assertThat(IndexStatus(phase = IndexPhase.LINKING, mode = Mode.FULL).isIndexing).isTrue()
        assertThat(IndexStatus(phase = IndexPhase.INDEXING, mode = Mode.INCREMENTAL).isIndexing).isFalse()
        assertThat(IndexStatus(phase = IndexPhase.IDLE, mode = Mode.FULL).isIndexing).isFalse()
        assertThat(IndexStatus().isIndexing).isFalse()
    }

    @Test
    fun `is running is false only when idle`() {
        assertThat(IndexStatus().isRunning).isFalse()
        assertThat(IndexStatus(phase = IndexPhase.PREPARING).isRunning).isTrue()
    }
}
