package com.kuriousdragon.smartindex.source.media.run

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SyncErrors
import org.junit.Test

class MediaRunStatsTest {
    @Test
    fun `toRunResult carries the counters and no error on success`() {
        val s = MediaRunStats(Mode.INCREMENTAL)
        s.written = 7
        s.deleted = 2
        s.skip("no_id")
        s.skip("bad_row")
        s.skip("bad_row")
        s.exclude("HIDDEN")
        val r = s.toRunResult().sources.single()
        assertThat(r.source).isEqualTo(Source.MEDIA_STORE)
        assertThat(r.mode).isEqualTo(Mode.INCREMENTAL)
        assertThat(r.written).isEqualTo(7)
        assertThat(r.skipped).isEqualTo(3)
        assertThat(r.deleted).isEqualTo(2)
        assertThat(r.error).isNull()
        assertThat(s.errorCode()).isNull()
    }

    @Test
    fun `every error maps to its lower-case code with the class or none`() {
        fun code(e: MediaRunError, t: Throwable?) = MediaRunStats(Mode.FULL).also { it.fail(e, t) }.errorCode()
        assertThat(code(MediaRunError.PERMISSION_REVOKED, SecurityException("m")))
            .isEqualTo("media_store:permission_revoked:SecurityException")
        assertThat(code(MediaRunError.PROVIDER_UNAVAILABLE, null)).isEqualTo("media_store:provider_unavailable:none")
        assertThat(code(MediaRunError.PROVIDER_ERROR, IllegalStateException())).isEqualTo(
            "media_store:provider_error:IllegalStateException",
        )
        assertThat(code(MediaRunError.WRITE_FAILED, IllegalArgumentException()))
            .isEqualTo("media_store:write_failed:IllegalArgumentException")
    }

    @Test
    fun `a full disk exception gives a STORAGE_FULL code that isStorageFullCode recognises`() {
        val s = MediaRunStats(Mode.FULL)
        s.fail(MediaRunError.WRITE_FAILED, RuntimeException("SQLITE_FULL: database or disk is full"))
        assertThat(s.errorCode()).isEqualTo("media_store:write_failed:STORAGE_FULL")
        assertThat(SyncErrors.isStorageFullCode(s.errorCode())).isTrue()
        assertThat(s.toRunResult().sources.single().error).contains("STORAGE_FULL")
    }
}
