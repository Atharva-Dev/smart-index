package com.kuriousdragon.smartindex.source.telephony.common

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.core.model.Source
import org.junit.Test

class RunResultsTest {
    @Test
    fun `a successful run maps counts and has no error`() {
        val stats = RunStatsBuilder(Source.CALL_LOG, Mode.INCREMENTAL).apply {
            written = 5
            deleted = 2
            skip("no_date")
            skip("no_date")
            skip("unknown_type")
            note("addr_budget")
        }
        val result = stats.build(10).toRunResult().sources.single()
        assertThat(result.source).isEqualTo(Source.CALL_LOG)
        assertThat(result.mode).isEqualTo(Mode.INCREMENTAL)
        assertThat(result.written).isEqualTo(5)
        assertThat(result.skipped).isEqualTo(3)
        assertThat(result.deleted).isEqualTo(2)
        assertThat(result.succeeded).isTrue()
    }

    @Test
    fun `a failed run carries source, error and class name only`() {
        val stats = RunStatsBuilder(Source.MMS, Mode.FULL).apply {
            fail(RunError.PERMISSION_REVOKED, SecurityException("secret +919876543210"))
        }
        val result = stats.build(10).toRunResult().sources.single()
        assertThat(result.error).isEqualTo("mms:permission_revoked:SecurityException")
        assertThat(result.succeeded).isFalse()
    }
}
