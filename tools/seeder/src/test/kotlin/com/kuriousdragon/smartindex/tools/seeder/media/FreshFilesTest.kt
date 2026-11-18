package com.kuriousdragon.smartindex.tools.seeder.media

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.tools.seeder.FreshType
import com.kuriousdragon.smartindex.tools.seeder.gen.MediaKind
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class FreshFilesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 11, 4, 9, 8, 7, 0, zone).toInstant().toEpochMilli()

    @Test
    fun namesAndLocations() {
        fun p(t: FreshType) = checkNotNull(FreshFiles.plan(t, "F01", now, zone))
        assertThat(p(FreshType.DOCUMENT).let { it.relativePath + it.displayName }).isEqualTo("Download/fresh-F01.pdf")
        assertThat(p(FreshType.OTHER).let { it.relativePath + it.displayName }).isEqualTo("Download/fresh-F01.zip")
        assertThat(p(FreshType.PHOTO).takenMs).isEqualTo(now)
        assertThat(p(FreshType.VIDEO).kind).isEqualTo(MediaKind.VIDEO)
        val rec = p(FreshType.RECORDING)
        assertThat(rec.relativePath).isEqualTo("Recordings/Call/")
        assertThat(rec.displayName).isEqualTo("Call recording Fresh F01_261104_090807.m4a")
        assertThat(rec.durationMs).isEqualTo(30_000L)
        assertThat(rec.modifiedMs).isEqualTo(now)
    }

    @Test
    fun telephonyTypesAreNotFiles() {
        assertThat(FreshFiles.plan(FreshType.SMS, "F01", now, zone)).isNull()
    }
}
