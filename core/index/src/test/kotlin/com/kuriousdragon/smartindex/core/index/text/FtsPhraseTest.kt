package com.kuriousdragon.smartindex.core.index.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FtsPhraseTest {
    @Test
    fun `wraps in quotes`() {
        assertThat(FtsPhrase.of("sea view")).isEqualTo("\"sea view\"")
    }

    @Test
    fun `doubles embedded quotes`() {
        assertThat(FtsPhrase.of("a\"b")).isEqualTo("\"a\"\"b\"")
    }

    @Test
    fun `empty string gives an empty phrase`() {
        assertThat(FtsPhrase.of("")).isEqualTo("\"\"")
    }

    @Test
    fun `a lone quote input gives four quotes`() {
        assertThat(FtsPhrase.of("\"\"")).isEqualTo("\"\"\"\"\"\"")
    }
}
