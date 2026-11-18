package com.kuriousdragon.smartindex.source.recordings.match

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.source.recordings.IST
import com.kuriousdragon.smartindex.source.recordings.LONDON
import com.kuriousdragon.smartindex.source.recordings.epoch
import com.kuriousdragon.smartindex.source.recordings.parse.ParsedRecordingName
import com.kuriousdragon.smartindex.source.recordings.recording
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Test

class AnchorResolverTest {
    private fun parsed(local: String) =
        ParsedRecordingName("p", "Rahul", null, LocalDateTime.parse(local), false)

    private fun utc(s: String) = LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `IST parse gives one PARSED anchor`() {
        val rec = recording(1, "x.m4a", null, "2024-11-03T09:45:00")
        val a = AnchorResolver.anchors(rec, parsed("2024-11-03T09:42:15"), IST)
        assertThat(a).containsExactly(Anchor(AnchorTier.PARSED, epoch("2024-11-03T09:42:15", IST)))
    }

    @Test
    fun `DST overlap gives two ascending anchors`() {
        val rec = recording(1, "x.m4a", null, "2024-10-27T02:00:00", zone = LONDON)
        val a = AnchorResolver.anchors(rec, parsed("2024-10-27T01:30:15"), LONDON)
        assertThat(a).containsExactly(
            Anchor(AnchorTier.PARSED, utc("2024-10-27T00:30:15")),
            Anchor(AnchorTier.PARSED, utc("2024-10-27T01:30:15")),
        ).inOrder()
    }

    @Test
    fun `DST gap gives one anchor shifted forward`() {
        val rec = recording(1, "x.m4a", null, "2024-03-31T02:00:00", zone = LONDON)
        val a = AnchorResolver.anchors(rec, parsed("2024-03-31T01:30:10"), LONDON)
        assertThat(a).containsExactly(Anchor(AnchorTier.PARSED, utc("2024-03-31T01:30:10")))
    }

    @Test
    fun `EVENT is added when different and dropped when equal to a PARSED anchor`() {
        val base = recording(1, "x.m4a", null, "2024-11-03T09:45:00")
        val parsedMs = epoch("2024-11-03T09:42:15", IST)
        val different = base.copy(dates = base.dates + (DateKind.EVENT to parsedMs + 5_000))
        assertThat(AnchorResolver.anchors(different, parsed("2024-11-03T09:42:15"), IST))
            .containsExactly(Anchor(AnchorTier.PARSED, parsedMs), Anchor(AnchorTier.EVENT, parsedMs + 5_000))
            .inOrder()
        val equal = base.copy(dates = base.dates + (DateKind.EVENT to parsedMs))
        assertThat(AnchorResolver.anchors(equal, parsed("2024-11-03T09:42:15"), IST))
            .containsExactly(Anchor(AnchorTier.PARSED, parsedMs))
    }

    @Test
    fun `MTIME is modified minus duration`() {
        val rec = recording(1, "Voice 001.m4a", 180, "2024-11-03T10:03:12")
        assertThat(AnchorResolver.anchors(rec, null, IST))
            .containsExactly(Anchor(AnchorTier.MTIME, epoch("2024-11-03T10:00:12", IST)))
    }

    @Test
    fun `MTIME falls back to ADDED then primaryTs`() {
        val rec = recording(1, "x.m4a", 60, "2024-11-03T10:01:00")
        val added = epoch("2024-11-03T10:02:00", IST)
        val withAdded = rec.copy(dates = mapOf(DateKind.ADDED to added))
        assertThat(
            AnchorResolver.anchors(withAdded, null, IST),
        ).containsExactly(Anchor(AnchorTier.MTIME, added - 60_000))
        val none = rec.copy(dates = emptyMap())
        assertThat(AnchorResolver.anchors(none, null, IST)).containsExactly(
            Anchor(
                AnchorTier.MTIME,
                rec.primaryTs - 60_000,
            ),
        )
    }

    @Test
    fun `no MTIME when duration is null or zero`() {
        assertThat(AnchorResolver.anchors(recording(1, "x.m4a", null, "2024-11-03T10:01:00"), null, IST)).isEmpty()
        assertThat(AnchorResolver.anchors(recording(1, "x.m4a", 0, "2024-11-03T10:01:00"), null, IST)).isEmpty()
    }

    @Test
    fun `unparsed recording has no PARSED anchor`() {
        val rec = recording(1, "x.m4a", 60, "2024-11-03T10:01:00")
        assertThat(AnchorResolver.anchors(rec, null, IST).map { it.tier }).containsExactly(AnchorTier.MTIME)
    }

    @Test
    fun `PARSED comes before MTIME`() {
        val rec = recording(1, "x.m4a", 60, "2024-11-03T10:01:00")
        val tiers = AnchorResolver.anchors(rec, parsed("2024-11-03T10:00:00"), IST).map { it.tier }
        assertThat(tiers).containsExactly(AnchorTier.PARSED, AnchorTier.MTIME).inOrder()
    }
}
