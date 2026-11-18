package com.kuriousdragon.smartindex.source.recordings.match

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.RecordingInputs
import com.kuriousdragon.smartindex.core.model.RecordingLink
import com.kuriousdragon.smartindex.source.recordings.IST
import com.kuriousdragon.smartindex.source.recordings.call
import com.kuriousdragon.smartindex.source.recordings.diag.RecordingDiagnostics
import com.kuriousdragon.smartindex.source.recordings.epoch
import com.kuriousdragon.smartindex.source.recordings.numberNormalizer
import com.kuriousdragon.smartindex.source.recordings.parse.RecordingNameParser
import com.kuriousdragon.smartindex.source.recordings.recording
import java.time.ZoneId
import java.util.Random
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MatchEngineTest {
    private fun engine(diagnostics: RecordingDiagnostics = RecordingDiagnostics.None) =
        MatchEngine(RecordingNameParser(), IdentityMatcher(numberNormalizer), diagnostics)

    private fun run(inputs: RecordingInputs, zone: ZoneId = IST) = runBlocking { engine().run(inputs, zone) }

    private fun links(inputs: RecordingInputs) = run(inputs).links

    private fun pairs(links: List<RecordingLink>) = links.map { it.callItemId to it.recordingItemId }

    private fun inputs(
        calls: List<RecordingInputs.Call>,
        recs: List<RecordingInputs.Recording>,
        links: List<RecordingLink> = emptyList(),
    ) = RecordingInputs(calls, recs, links)

    private fun rec(id: Long, who: String, hhmmss: String, durS: Long?, suffix: String = "") =
        recording(id, "Call recording ${who}_241103_$hhmmss$suffix.m4a", durS, "2024-11-03T23:00:00")

    // ---- worked examples ----

    @Test
    fun `E1 contact call links with name+ts at 9676`() {
        val out = links(inputs(listOf(call(1, "2024-11-03T09:41:58", 185)), listOf(rec(11, "Rahul", "094215", 184))))
        assertThat(out).hasSize(1)
        assertThat(out[0].score).isEqualTo(0.9676f)
        assertThat(out[0].method).isEqualTo("name+ts")
    }

    @Test
    fun `E2 unknown number links by number`() {
        val c = call(1, "2024-11-03T10:09:50", 42, number = "+919876543210", name = null)
        val r = recording(11, "Call recording +91 98765 43210_241103_101005.m4a", 41, "2024-11-03T23:00:00")
        val out = links(inputs(listOf(c), listOf(r)))
        assertThat(out.map { it.method }).containsExactly("number+ts")
    }

    @Test
    fun `E4 back-to-back calls with the same contact link A to rA and B to rB`() {
        val calls = listOf(call(1, "2024-11-03T10:00:00", 40), call(2, "2024-11-03T10:01:10", 120))
        val recs = listOf(rec(11, "Rahul", "100012", 40), rec(12, "Rahul", "100118", 119))
        assertThat(pairs(links(inputs(calls, recs)))).containsExactly(1L to 11L, 2L to 12L).inOrder()
        assertThat(links(inputs(calls, recs)).map { (it.score * 10_000).toInt() }).containsExactly(10000, 9500)
    }

    @Test
    fun `E5 two calls within a minute link A to rA and B to rB`() {
        val calls = listOf(call(1, "2024-11-03T10:00:00", 20), call(2, "2024-11-03T10:00:50", 22))
        val recs = listOf(rec(11, "Rahul", "100005", 20), rec(12, "Rahul", "100055", 22))
        assertThat(pairs(links(inputs(calls, recs)))).containsExactly(1L to 11L, 2L to 12L).inOrder()
    }

    @Test
    fun `E5b a second run with B present moves rB from A to B`() {
        val a = call(1, "2024-11-03T10:00:00", 20)
        val b = call(2, "2024-11-03T10:00:50", 22)
        val rB = rec(12, "Rahul", "100055", 22)

        val first = links(inputs(listOf(a), listOf(rB)))
        assertThat(pairs(first)).containsExactly(1L to 12L)
        assertThat((first[0].score * 10_000).toInt()).isEqualTo(7050)

        val second = links(inputs(listOf(a, b), listOf(rB), first))
        assertThat(pairs(second)).containsExactly(2L to 12L)
    }

    @Test
    fun `E6 unparseable name links by modified time minus duration`() {
        val c = call(1, "2024-11-03T10:00:00", 181)
        val r = recording(11, "Voice 001.m4a", 180, "2024-11-03T10:03:12")
        val out = links(inputs(listOf(c), listOf(r)))
        assertThat(out.map { (it.score * 10_000).toInt() to it.method }).containsExactly(6169 to "time+mtime")
    }

    @Test
    fun `E8 missed, rejected, blocked and zero-duration calls are never linked`() {
        val calls =
            listOf(
                call(1, "2024-11-03T10:00:00", 0, dir = Direction.MISSED),
                call(2, "2024-11-03T10:00:00", 60, dir = Direction.MISSED),
                call(3, "2024-11-03T10:00:00", 60, dir = Direction.REJECTED),
                call(4, "2024-11-03T10:00:00", 60, dir = Direction.BLOCKED),
                call(5, "2024-11-03T10:00:00", 0),
                call(6, "2024-11-03T10:00:00", null),
            )
        val recs = listOf(rec(11, "Rahul", "100008", 60))
        assertThat(links(inputs(calls, recs))).isEmpty()
    }

    @Test
    fun `E8 an answered call after a missed call gets the link`() {
        val calls =
            listOf(
                call(1, "2024-11-03T10:00:00", 0, dir = Direction.MISSED),
                call(2, "2024-11-03T10:00:40", 60),
            )
        val out = links(inputs(calls, listOf(rec(11, "Rahul", "100048", 60))))
        assertThat(pairs(out)).containsExactly(2L to 11L)
        assertThat(out[0].score).isEqualTo(1.0f)
    }

    @Test
    fun `E9 the copy loses`() {
        val c = call(1, "2024-11-03T14:00:00", 300, number = null, name = "Mom")
        val recs = listOf(rec(501, "Mom", "140010", 299), rec(502, "Mom", "140010", 299, suffix = " (1)"))
        val result = run(inputs(listOf(c), recs))
        assertThat(pairs(result.links)).containsExactly(1L to 501L)
        assertThat(result.links[0].score).isEqualTo(0.98f)
        assertThat(result.stats.unlinkedLost).isEqualTo(1)
        // and in the opposite id order the copy still loses
        val swapped = listOf(rec(502, "Mom", "140010", 299), rec(501, "Mom", "140010", 299, suffix = " (1)"))
        assertThat(pairs(links(inputs(listOf(c), swapped)))).containsExactly(1L to 502L)
    }

    @Test
    fun `E10 renamed contact keeps an existing link and is rejected without one`() {
        val c = call(1, "2024-11-03T09:41:58", 185, name = "Bhai")
        val r = rec(11, "Rahul", "094215", 184)
        val existing = listOf(RecordingLink(1, 11, 0.9f, "name+ts"))

        val kept = links(inputs(listOf(c), listOf(r), existing))
        assertThat(kept.map { (it.score * 10_000).toInt() to it.method }).containsExactly(7176 to "time+ts")

        val fresh = run(inputs(listOf(c), listOf(r)))
        assertThat(fresh.links).isEmpty()
        assertThat(fresh.stats.contradictions).isEqualTo(1)
    }

    @Test
    fun `E11 a different contact is never linked`() {
        val c = call(1, "2024-11-03T09:41:58", 185, name = "Priya", number = null)
        assertThat(links(inputs(listOf(c), listOf(rec(11, "Rahul", "094215", 185))))).isEmpty()
    }

    @Test
    fun `a different number in the file is never linked even when the names match`() {
        val c = call(1, "2024-11-03T09:41:58", 185, number = "+919876543210", name = "Rahul")
        val r = recording(11, "Rahul_9123456780_241103_094215.m4a", 185, "2024-11-03T23:00:00")
        assertThat(links(inputs(listOf(c), listOf(r)))).isEmpty()
    }

    @Test
    fun `conference call gives at most one link and is identical across shuffles`() {
        val calls =
            listOf(
                call(1, "2024-11-03T10:00:00", 300, number = "+919876543210", name = "Alice"),
                call(2, "2024-11-03T10:00:05", 300, number = "+919123456780", name = "Bob"),
            )
        val recs = listOf(rec(11, "Conference call", "100010", 300))
        val expected = links(inputs(calls, recs))
        assertThat(expected.size).isAtMost(1)
        repeat(10) { seed ->
            val rnd = Random(seed.toLong())
            assertThat(links(inputs(calls.shuffled(rnd), recs.shuffled(rnd)))).isEqualTo(expected)
        }
    }

    @Test
    fun `unknown number in the file links by time and number`() {
        val c = call(1, "2024-11-03T10:09:50", 42, number = "+919876543210", name = null)
        val r = recording(11, "Call recording 9876543210_241103_101005.amr", 42, "2024-11-03T23:00:00")
        assertThat(links(inputs(listOf(c), listOf(r))).map { it.method }).containsExactly("number+ts")
    }

    @Test
    fun `private number with a placeholder name links by time`() {
        val c = call(1, "2024-11-03T10:09:50", 42, number = null, name = null)
        val r = rec(11, "Private number", "101005", 42)
        assertThat(links(inputs(listOf(c), listOf(r))).map { it.method }).containsExactly("time+ts")
    }

    @Test
    fun `a deleted recording leaves its call unlinked`() {
        val c = call(1, "2024-11-03T09:41:58", 185)
        assertThat(links(inputs(listOf(c), emptyList()))).isEmpty()
    }

    @Test
    fun `empty file is skipped and counted`() {
        val c = call(1, "2024-11-03T09:41:58", 185)
        val r = recording(11, "Call recording Rahul_241103_094215.m4a", 184, "2024-11-03T23:00:00", sizeBytes = 0)
        val result = run(inputs(listOf(c), listOf(r)))
        assertThat(result.links).isEmpty()
        assertThat(result.stats.emptyFiles).isEqualTo(1)
    }

    @Test
    fun `a recording without any anchor is counted as noAnchor`() {
        val c = call(1, "2024-11-03T09:41:58", 185)
        val r = recording(11, "Voice 001.m4a", null, "2024-11-03T09:45:00")
        val result = run(inputs(listOf(c), listOf(r)))
        assertThat(result.links).isEmpty()
        assertThat(result.stats.noAnchor).isEqualTo(1)
        assertThat(result.stats.unparsed).isEqualTo(1)
    }

    @Test
    fun `EVENT anchor is used when the name is unparsed`() {
        val c = call(1, "2024-11-03T10:00:00", 60)
        val base = recording(11, "Voice 001.m4a", 60, "2024-11-03T23:00:00")
        val r = base.copy(dates = base.dates + (DateKind.EVENT to epoch("2024-11-03T10:00:10")))
        val out = links(inputs(listOf(c), listOf(r)))
        assertThat(out.map { it.method }).containsExactly("time+event")
    }

    @Test
    fun `duplicate ids keep the first occurrence`() {
        val c = call(1, "2024-11-03T09:41:58", 185)
        val r = rec(11, "Rahul", "094215", 184)
        val out = links(inputs(listOf(c, c.copy(contactName = "Priya")), listOf(r, r)))
        assertThat(pairs(out)).containsExactly(1L to 11L)
    }

    // ---- incremental ----

    @Test
    fun `a link whose call is outside the inputs is frozen and not emitted`() {
        val c2 = call(2, "2024-11-03T09:41:58", 185)
        val r = rec(11, "Rahul", "094215", 184)
        val frozen = listOf(RecordingLink(1, 11, 0.9f, "name+ts"))
        val result = run(inputs(listOf(c2), listOf(r), frozen))
        assertThat(result.stats.frozenLinks).isEqualTo(1)
        assertThat(result.links).isEmpty()
    }

    @Test
    fun `a link whose recording is outside the inputs freezes its call`() {
        val c = call(1, "2024-11-03T09:41:58", 185)
        val other = rec(12, "Rahul", "094215", 184)
        val frozen = listOf(RecordingLink(1, 99, 0.9f, "name+ts"))
        val result = run(inputs(listOf(c), listOf(other), frozen))
        assertThat(result.stats.frozenLinks).isEqualTo(1)
        assertThat(result.links).isEmpty()
    }

    @Test
    fun `a link with both sides inside is re-emitted`() {
        val c = call(1, "2024-11-03T09:41:58", 185)
        val r = rec(11, "Rahul", "094215", 184)
        val existing = listOf(RecordingLink(1, 11, 0.9676f, "name+ts"))
        val result = run(inputs(listOf(c), listOf(r), existing))
        assertThat(result.links).isEqualTo(existing)
        assertThat(result.stats.frozenLinks).isEqualTo(0)
    }

    @Test
    fun `empty inputs give an empty result`() {
        val result = run(inputs(emptyList(), emptyList()))
        assertThat(result.links).isEmpty()
        assertThat(result.stats.linked).isEqualTo(0)
    }

    @Test
    fun `diagnostics get unparsed names and the final stats`() {
        val unparsed = ArrayList<String>()
        val finished = ArrayList<MatchStats>()
        val diag =
            object : RecordingDiagnostics {
                override fun onUnparsed(fileName: String) {
                    unparsed += fileName
                }

                override fun onRunFinished(stats: MatchStats) {
                    finished += stats
                }
            }
        val c = call(1, "2024-11-03T10:00:00", 181)
        val r = recording(11, "Voice 001.m4a", 180, "2024-11-03T10:03:12")
        runBlocking { engine(diag).run(inputs(listOf(c), listOf(r)), IST) }
        assertThat(unparsed).containsExactly("Voice 001.m4a")
        assertThat(finished).hasSize(1)
        assertThat(finished[0].linked).isEqualTo(1)
    }

    @Test
    fun `an exception while handling one recording skips it and counts noAnchor`() {
        val boom =
            object : RecordingDiagnostics {
                override fun onUnparsed(fileName: String) = error("boom")

                override fun onRunFinished(stats: MatchStats) = Unit
            }
        val c = call(1, "2024-11-03T10:00:00", 181)
        val bad = recording(11, "Voice 001.m4a", 180, "2024-11-03T10:03:12")
        val good = rec(12, "Rahul", "100012", 181)
        val result = runBlocking { engine(boom).run(inputs(listOf(c), listOf(bad, good)), IST) }
        assertThat(result.stats.noAnchor).isEqualTo(1)
        assertThat(pairs(result.links)).containsExactly(1L to 12L)
    }

    // ---- invariants over random scenarios ----

    private fun scenario(seed: Long): RecordingInputs {
        val rnd = Random(seed)
        val names = listOf("Rahul", "Priya", "Mom", "Rahul Sharma", "Alex")
        val dirs =
            listOf(Direction.INCOMING, Direction.OUTGOING, Direction.MISSED, Direction.REJECTED, Direction.BLOCKED)
        val base = epoch("2024-11-03T08:00:00")
        val callCount = 5 + rnd.nextInt(25)
        val calls = ArrayList<RecordingInputs.Call>()
        var ts = base
        for (i in 1..callCount) {
            ts += (5 + rnd.nextInt(120)) * 1000L
            val dur = if (rnd.nextInt(8) == 0) 0L else (10 + rnd.nextInt(300)) * 1000L
            val withNumber = rnd.nextInt(3) != 0
            val n = names[rnd.nextInt(names.size)]
            calls +=
                RecordingInputs.Call(
                    id = i.toLong(),
                    primaryTs = ts,
                    durationMs = dur,
                    numberNorm = if (withNumber) "+9198765432${10 + rnd.nextInt(3)}" else null,
                    numberRaw = null,
                    contactName = if (rnd.nextInt(4) == 0) null else n,
                    direction = dirs[rnd.nextInt(if (rnd.nextInt(3) == 0) dirs.size else 2)],
                )
        }
        val recs = ArrayList<RecordingInputs.Recording>()
        var rid = 100L
        for (c in calls) {
            if (rnd.nextInt(4) == 0) continue
            val start = c.primaryTs + rnd.nextInt(40_000) - 5_000
            val dur = (c.durationMs ?: 0L) + rnd.nextInt(12_000) - 6_000
            val local = java.time.Instant.ofEpochMilli(start).atZone(IST).toLocalDateTime()
            val stamp = "%02d%02d%02d_%02d%02d%02d".format(
                local.year % 100,
                local.monthValue,
                local.dayOfMonth,
                local.hour,
                local.minute,
                local.second,
            )
            val unparsedName = rnd.nextInt(5) == 0
            val title =
                if (unparsedName) {
                    "Voice ${rid + 1}.m4a"
                } else {
                    "Call recording ${names[
                        rnd.nextInt(
                            names.size,
                        ),
                    ]}_$stamp${if (rnd.nextInt(6) == 0) " (1)" else ""}.m4a"
                }
            val modified = start + maxOf(dur, 0L)
            recs +=
                RecordingInputs.Recording(
                    id = ++rid,
                    title = title,
                    folder = null,
                    primaryTs = modified,
                    dates = mapOf(DateKind.MODIFIED to modified),
                    durationMs = if (rnd.nextInt(10) == 0) null else dur,
                    sizeBytes = 1000,
                )
        }
        val links = ArrayList<RecordingLink>()
        if (recs.isNotEmpty() && rnd.nextBoolean()) {
            links += RecordingLink(calls[0].id, recs[0].id, 0.9f, "name+ts")
        }
        return RecordingInputs(calls, recs, links)
    }

    @Test
    fun `invariants hold on 200 random scenarios and shuffling the input changes nothing`() {
        val methodRegex = Regex("^(number|name|partial|time)\\+(ts|event|mtime)$")
        repeat(200) { n ->
            val seed = n.toLong()
            val input = scenario(seed)
            val out = links(input)

            assertThat(out.map { it.callItemId }.toSet()).hasSize(out.size)
            assertThat(out.map { it.recordingItemId }.toSet()).hasSize(out.size)
            assertThat(out.map { it.callItemId }).isInOrder()
            val callsById = input.calls.associateBy { it.id }
            val recsById = input.recordings.associateBy { it.id }
            for (l in out) {
                assertThat(l.score).isAtLeast(0.5f)
                assertThat(l.score).isAtMost(1.0f)
                assertThat(l.method).matches(methodRegex.toPattern())
                val c = callsById.getValue(l.callItemId)
                assertThat(c.durationMs ?: 0L).isGreaterThan(0L)
                assertThat(c.direction).isNotIn(listOf(Direction.MISSED, Direction.REJECTED, Direction.BLOCKED))
                val r = recsById.getValue(l.recordingItemId)
                val parsed = RecordingNameParser().parse(r.title)
                val anchors = AnchorResolver.anchors(r, parsed, IST)
                val inWindow = anchors.any {
                    val d = it.atMs - c.primaryTs
                    d >= -MatcherConfig.WINDOW_BEFORE_MS && d <= MatcherConfig.WINDOW_AFTER_MS
                }
                assertThat(inWindow).isTrue()
                val rd = r.durationMs
                if (rd != null && rd > 0) {
                    assertThat(kotlin.math.abs(rd - c.durationMs!!))
                        .isAtMost(RecordingScorer.durationTolerance(c.durationMs!!))
                }
            }

            val rnd = Random(seed * 31 + 7)
            val shuffled =
                RecordingInputs(input.calls.shuffled(rnd), input.recordings.shuffled(rnd), input.links.shuffled(rnd))
            assertThat(links(shuffled)).isEqualTo(out)
        }
    }

    @Test
    fun `incremental output never contains the in-window side of a frozen link`() {
        repeat(50) { n ->
            val input = scenario(1000L + n)
            if (input.recordings.size < 2) return@repeat
            // Pretend the call of the first link target lies outside the window: drop call 3 and link it.
            val dropped = input.calls.first { it.id == 3L }
            val rec = input.recordings.first()
            val frozen = RecordingLink(dropped.id, rec.id, 0.9f, "name+ts")
            val reduced = RecordingInputs(input.calls.filter { it.id != dropped.id }, input.recordings, listOf(frozen))
            val result = run(reduced)
            assertThat(result.links.map { it.recordingItemId }).doesNotContain(rec.id)
            assertThat(result.stats.frozenLinks).isEqualTo(1)
        }
    }
}
