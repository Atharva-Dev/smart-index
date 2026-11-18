package com.kuriousdragon.smartindex.source.recordings.match

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecordingScorerTest {
    private fun s(delta: Long, rec: Long?, call: Long, id: Identity, tier: AnchorTier) =
        RecordingScorer.score(delta, rec, call, id, tier)

    @Test
    fun `timeScore boundaries`() {
        assertThat(RecordingScorer.timeScore(-10_001)).isNull()
        assertThat(RecordingScorer.timeScore(-10_000)).isEqualTo(0.0)
        assertThat(RecordingScorer.timeScore(-5_000)).isEqualTo(0.5)
        assertThat(RecordingScorer.timeScore(0)).isEqualTo(1.0)
        assertThat(RecordingScorer.timeScore(20_000)).isEqualTo(1.0)
        assertThat(RecordingScorer.timeScore(55_000)).isEqualTo(0.5)
        assertThat(RecordingScorer.timeScore(90_000)).isEqualTo(0.0)
        assertThat(RecordingScorer.timeScore(90_001)).isNull()
    }

    @Test
    fun `durationTolerance values`() {
        assertThat(RecordingScorer.durationTolerance(60_000)).isEqualTo(5_000)
        assertThat(RecordingScorer.durationTolerance(100_000)).isEqualTo(5_000)
        assertThat(RecordingScorer.durationTolerance(185_000)).isEqualTo(9_250)
        assertThat(RecordingScorer.durationTolerance(200_000)).isEqualTo(10_000)
        assertThat(RecordingScorer.durationTolerance(3_600_000)).isEqualTo(180_000)
    }

    @Test
    fun `duration gate passes exactly at tolerance and fails one ms beyond`() {
        assertThat(s(0, 65_000, 60_000, Identity.MATCH_NAME, AnchorTier.PARSED)).isNotNull()
        assertThat(s(0, 65_001, 60_000, Identity.MATCH_NAME, AnchorTier.PARSED)).isNull()
        assertThat(s(0, 55_000, 60_000, Identity.MATCH_NAME, AnchorTier.PARSED)).isNotNull()
        assertThat(s(0, 54_999, 60_000, Identity.MATCH_NAME, AnchorTier.PARSED)).isNull()
    }

    @Test
    fun `unknown recording duration needs a match identity`() {
        for (unknown in listOf<Long?>(null, 0L, -5L)) {
            assertThat(s(0, unknown, 60_000, Identity.MATCH_NUMBER, AnchorTier.PARSED)).isNotNull()
            assertThat(s(0, unknown, 60_000, Identity.MATCH_NAME, AnchorTier.PARSED)).isNotNull()
            assertThat(s(0, unknown, 60_000, Identity.PARTIAL_NAME, AnchorTier.PARSED)).isNull()
            assertThat(s(0, unknown, 60_000, Identity.UNKNOWN, AnchorTier.PARSED)).isNull()
        }
    }

    @Test
    fun `contradictions are rejected`() {
        assertThat(s(0, 60_000, 60_000, Identity.CONTRADICT_NAME, AnchorTier.PARSED)).isNull()
        assertThat(s(0, 60_000, 60_000, Identity.CONTRADICT_NUMBER, AnchorTier.PARSED)).isNull()
    }

    @Test
    fun `below threshold is rejected and exactly threshold is kept`() {
        // UNKNOWN + MTIME: 0.35 * sT + 0.30 * sD; sD = 0 at the tolerance edge gives 0.35 < 0.5
        assertThat(s(0, 55_000, 60_000, Identity.UNKNOWN, AnchorTier.MTIME)).isNull()
        val edge = s(0, 57_500, 60_000, Identity.UNKNOWN, AnchorTier.MTIME) // sD 0.5 -> 0.50
        assertThat(edge?.scoreBp).isEqualTo(5_000)
    }

    @Test
    fun `E1 contact call name plus ts`() {
        assertThat(s(17_000, 184_000, 185_000, Identity.MATCH_NAME, AnchorTier.PARSED))
            .isEqualTo(PairScore(9676, "name+ts"))
    }

    @Test
    fun `E2 unknown number`() {
        assertThat(s(15_000, 41_500, 42_000, Identity.MATCH_NUMBER, AnchorTier.PARSED))
            .isEqualTo(PairScore(9700, "number+ts"))
    }

    @Test
    fun `E3 contacts denied`() {
        assertThat(s(17_000, 184_000, 185_000, Identity.UNKNOWN, AnchorTier.PARSED))
            .isEqualTo(PairScore(7176, "time+ts"))
    }

    @Test
    fun `E4 back to back same contact`() {
        assertThat(s(12_000, 40_000, 40_000, Identity.MATCH_NAME, AnchorTier.PARSED)?.scoreBp).isEqualTo(10_000)
        assertThat(s(-58_000, 40_000, 120_000, Identity.MATCH_NAME, AnchorTier.PARSED)).isNull()
        assertThat(s(78_000, 119_000, 40_000, Identity.MATCH_NAME, AnchorTier.PARSED)).isNull()
        assertThat(s(8_000, 119_000, 120_000, Identity.MATCH_NAME, AnchorTier.PARSED)?.scoreBp).isEqualTo(9_500)
    }

    @Test
    fun `E5 two calls within a minute`() {
        assertThat(s(5_000, 20_000, 20_000, Identity.MATCH_NAME, AnchorTier.PARSED)?.scoreBp).isEqualTo(10_000)
        assertThat(s(5_000, 22_000, 22_000, Identity.MATCH_NAME, AnchorTier.PARSED)?.scoreBp).isEqualTo(10_000)
        assertThat(s(55_000, 22_000, 20_000, Identity.MATCH_NAME, AnchorTier.PARSED)?.scoreBp).isEqualTo(7_050)
        assertThat(s(-45_000, 20_000, 22_000, Identity.MATCH_NAME, AnchorTier.PARSED)).isNull()
    }

    @Test
    fun `E6 unparseable name via mtime`() {
        assertThat(s(12_000, 180_000, 181_000, Identity.UNKNOWN, AnchorTier.MTIME))
            .isEqualTo(PairScore(6169, "time+mtime"))
    }

    @Test
    fun `E6b duration difference of 4 s links and 5 s does not`() {
        assertThat(s(12_000, 185_000, 181_000, Identity.UNKNOWN, AnchorTier.MTIME)?.scoreBp).isEqualTo(5_174)
        assertThat(s(12_000, 186_000, 181_000, Identity.UNKNOWN, AnchorTier.MTIME)).isNull()
    }

    @Test
    fun `E7 time zone travel via mtime`() {
        assertThat(s(3_000, 184_000, 185_000, Identity.MATCH_NAME, AnchorTier.MTIME))
            .isEqualTo(PairScore(8676, "name+mtime"))
    }

    @Test
    fun `E9 copy of a recording`() {
        assertThat(s(10_000, 299_000, 300_000, Identity.MATCH_NAME, AnchorTier.PARSED)?.scoreBp).isEqualTo(9_800)
    }

    @Test
    fun `E12 recording without duration`() {
        assertThat(s(10_000, null, 60_000, Identity.MATCH_NAME, AnchorTier.PARSED))
            .isEqualTo(PairScore(7000, "name+ts"))
        assertThat(s(10_000, null, 60_000, Identity.UNKNOWN, AnchorTier.PARSED)).isNull()
    }
}
