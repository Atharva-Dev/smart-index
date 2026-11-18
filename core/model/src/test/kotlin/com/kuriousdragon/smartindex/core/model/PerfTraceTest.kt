package com.kuriousdragon.smartindex.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PerfTraceTest {
    @Test
    fun `begin end and traced are no-ops on plain JVM`() {
        val cookie = PerfTrace.nextCookie()
        PerfTrace.begin(PerfTrace.SEARCH_FIRST_PAGE, cookie)
        PerfTrace.end(PerfTrace.SEARCH_FIRST_PAGE, cookie)
        assertThat(PerfTrace.traced(PerfTrace.STORE_PAGE) { 42 }).isEqualTo(42)
    }

    @Test
    fun `traced rethrows the block exception`() {
        val result = runCatching { PerfTrace.traced<Unit>(PerfTrace.STORE_COUNTS) { error("boom") } }
        assertThat(result.exceptionOrNull()).hasMessageThat().isEqualTo("boom")
    }

    @Test
    fun `nextCookie is strictly increasing`() {
        val cookies = List(5) { PerfTrace.nextCookie() }
        assertThat(cookies).isInOrder()
        assertThat(cookies.toSet()).hasSize(5)
    }

    @Test
    fun `section names are the contract`() {
        assertThat(PerfTrace.INDEX_FULL).isEqualTo("SIDX:index:full")
        assertThat(PerfTrace.SEARCH_FIRST_PAGE).isEqualTo("SIDX:search:firstPage")
    }
}
