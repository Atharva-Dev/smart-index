package com.kuriousdragon.smartindex.tools.seeder.gen

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.source.recordings.parse.RecordingNameParser
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.max
import org.junit.Test

class RecordingPlanTest {
    private val parser = RecordingNameParser()

    private fun check(plan: SeedPlan) {
        val calls = plan.calls.associateBy { it.key }
        val sortedStarts = plan.calls.map { it.startMs }.sorted()
        val contactByTen = HashMap<String, SeedContact>()
        for (c in plan.contacts) for (n in c.numbers) contactByTen[Numbers.lastTen(n)] = c

        fun anchorMs(r: SeedRecording): Long {
            val parsed = parser.parse(r.file.displayName)
            return if (parsed != null) {
                ZonedDateTime.ofLocal(parsed.localDateTime, plan.zone, null).toInstant().toEpochMilli()
            } else {
                r.file.modifiedMs - r.file.durationMs!!
            }
        }
        fun delta(r: SeedRecording) = anchorMs(r) - calls.getValue(r.callKey!!).startMs
        fun durationOk(r: SeedRecording): Boolean {
            val callMs = calls.getValue(r.callKey!!).durationSec * 1000L
            return abs(r.file.durationMs!! - callMs) <= max(5_000.0, 0.05 * callMs)
        }
        fun inWindow(a: Long) = sortedStarts.count { it >= a - 90_000 && it <= a + 10_000 }

        assertThat(plan.recordings).isNotEmpty()
        for (r in plan.recordings) {
            val name = r.file.displayName
            val parsed = parser.parse(name)
            if (r.case == "E6") {
                assertThat(parsed).isNull()
            } else {
                assertThat(parsed).isNotNull()
                parsed!!
                assertThat(parsed.patternId).isEqualTo(r.patternId)
                assertThat(parsed.name).isEqualTo(r.expectName)
                assertThat(parsed.number).isEqualTo(r.expectNumber)
                assertThat(parsed.localDateTime).isEqualTo(r.localStart)
            }
            when {
                r.case == "E9copy" -> {
                    assertThat(parsed!!.isCopy).isTrue()
                    assertThat(r.expectLinked).isFalse()
                }

                r.expectLinked -> {
                    val d = delta(r)
                    assertThat(d).isAtLeast(-10_000L)
                    assertThat(d).isAtMost(90_000L)
                    assertThat(durationOk(r)).isTrue()
                    val call = calls.getValue(r.callKey!!)
                    assertThat(call.type).isAnyOf(1, 2)
                    if (r.case == "BULK") assertThat(inWindow(anchorMs(r))).isEqualTo(1)
                }

                r.case == "N1" -> assertThat(calls.getValue(r.callKey!!).type).isEqualTo(3)

                r.case == "N2" -> {
                    val d = delta(r)
                    assertThat(d < -10_000L || d > 90_000L).isTrue()
                }

                r.case == "N3" -> {
                    assertThat(delta(r)).isAtLeast(-10_000L)
                    assertThat(delta(r)).isAtMost(90_000L)
                    assertThat(durationOk(r)).isFalse()
                }

                r.case == "N4" -> {
                    val call = calls.getValue(r.callKey!!)
                    val who = contactByTen.getValue(Numbers.lastTen(call.number!!)).displayName
                    assertThat(who.lowercase()).isNotEqualTo(parsed!!.name!!.lowercase())
                    assertThat(delta(r)).isAtLeast(-10_000L)
                    assertThat(durationOk(r)).isTrue()
                }

                r.case == "N5" -> {
                    assertThat(r.callKey).isNull()
                    val a = anchorMs(r)
                    assertThat(sortedStarts.none { abs(it - a) <= 600_000 }).isTrue()
                }

                else -> error("unexpected unlinked case ${r.case}")
            }
        }
    }

    @Test
    fun `small plan recordings parse as planned and obey the gates`() = check(TestPlans.small)

    @Test
    fun `edge plan recordings parse as planned and obey the gates`() = check(TestPlans.edge)

    @Test(timeout = 120_000)
    fun `nfr002 plan recordings parse as planned and obey the gates`() = check(TestPlans.nfr002)

    @Test
    fun `every tricky case is present in every profile`() {
        for (plan in listOf(TestPlans.small, TestPlans.edge)) {
            val cases = plan.recordings.map { it.case }
            assertThat(cases).containsAtLeast(
                "E1", "E2", "E4", "E5", "E6", "E8", "E9", "E9copy", "PRIVATE", "N1", "N2", "N3", "N4", "N5",
            )
            assertThat(cases.count { it == "E4" }).isEqualTo(2)
            assertThat(cases.count { it == "N2" } >= 2).isTrue()
        }
    }

    @Test
    fun `N6 voice memo is an other file in the voice recorder folder and never a recording`() {
        val plan = TestPlans.small
        val memo = plan.files.single {
            it.relativePath == "Recordings/Voice Recorder/" && it.displayName.startsWith("memo_")
        }
        assertThat(memo.kind).isEqualTo(MediaKind.OTHER)
        assertThat(plan.recordings.map { it.file.displayName }).doesNotContain(memo.displayName)
        assertThat(plan.planted.any { it.id == "N6" }).isTrue()
    }

    @Test
    fun `bulk name mix covers every form`() {
        val bulk = TestPlans.nfr002.recordings.filter { it.case == "BULK" }
        val patterns = bulk.map { it.patternId }.toSet()
        assertThat(patterns).containsExactly(
            RecordingPlan.PATTERN_YYMMDD,
            RecordingPlan.PATTERN_YYYYMMDD,
            RecordingPlan.PATTERN_NUMBER,
        )
        val legacy = bulk.count { it.file.relativePath == "Call/" && it.file.displayName.endsWith(".amr") }
        assertThat(legacy).isGreaterThan(0)
        assertThat(bulk.count { it.file.displayName.startsWith("कॉल रिकॉर्डिंग") }).isGreaterThan(0)
        val named = bulk.count {
            it.file.displayName.startsWith("Call recording ") && it.expectName != null &&
                it.patternId == RecordingPlan.PATTERN_YYMMDD
        }
        assertThat(named.toDouble() / bulk.size).isWithin(0.10).of(0.65)
    }
}
