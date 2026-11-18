package com.kuriousdragon.smartindex.source.recordings.match

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.DateKind
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.RecordingInputs
import com.kuriousdragon.smartindex.source.recordings.IST
import com.kuriousdragon.smartindex.source.recordings.diag.RecordingDiagnostics
import com.kuriousdragon.smartindex.source.recordings.epoch
import com.kuriousdragon.smartindex.source.recordings.numberNormalizer
import com.kuriousdragon.smartindex.source.recordings.parse.RecordingNameParser
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Random
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MatcherPerformanceTest {
    private val stamp = DateTimeFormatter.ofPattern("yyMMdd_HHmmss").withZone(IST)

    @Test
    fun `5000 recordings against 50000 calls link in under one second`() {
        val rnd = Random(42)
        val calls = ArrayList<RecordingInputs.Call>(CALLS)
        var ts = epoch("2024-01-01T00:00:00")
        val yearMs = 365L * 24 * 3600 * 1000
        val avgGap = yearMs / CALLS
        repeat(CALLS) { i ->
            val burst = i > 0 && rnd.nextInt(100) < 5
            ts += if (burst) (20 + rnd.nextInt(41)) * 1000L else minOf(avgGap, (1 + rnd.nextInt(30)) * 60_000L) + 1000L
            val missed = rnd.nextInt(100) < 10
            calls +=
                RecordingInputs.Call(
                    id = i + 1L,
                    primaryTs = ts,
                    durationMs = if (missed) 0L else (15 + rnd.nextInt(120)) * 1000L,
                    numberNorm = null,
                    numberRaw = null,
                    contactName = "Contact$i",
                    direction = if (missed) Direction.MISSED else Direction.INCOMING,
                )
        }
        val answered = calls.filter { it.direction != Direction.MISSED }.shuffled(rnd)
        val parsedCalls = answered.take(PARSED)
        val unparsedCalls = answered.drop(PARSED).take(UNPARSED)

        val recordings = ArrayList<RecordingInputs.Recording>()
        val ownCall = HashMap<Long, Long>()
        var rid = 1_000_000L
        for (c in parsedCalls) {
            val start = c.primaryTs + (5 + rnd.nextInt(10)) * 1000L
            val title = "Call recording ${c.contactName}_${stamp.format(Instant.ofEpochMilli(start))}.m4a"
            val modified = start + c.durationMs!!
            recordings += recording(++rid, title, c.durationMs!!, modified)
            ownCall[rid] = c.id
        }
        for (c in unparsedCalls) {
            val start = c.primaryTs + (5 + rnd.nextInt(10)) * 1000L
            recordings += recording(++rid, "Voice $rid.m4a", c.durationMs!!, start + c.durationMs!!)
        }
        val inputs = RecordingInputs(calls, recordings, emptyList())
        val engine = MatchEngine(RecordingNameParser(), IdentityMatcher(numberNormalizer), RecordingDiagnostics.None)

        val warm = runBlocking { engine.run(inputs, IST) }
        var best = Long.MAX_VALUE
        repeat(3) {
            val t0 = System.nanoTime()
            runBlocking { engine.run(inputs, IST) }
            best = minOf(best, (System.nanoTime() - t0) / 1_000_000L)
        }

        assertThat(best).isLessThan(1_000L)
        assertThat(warm.links.size).isAtLeast(PARSED)
        val byRecording = warm.links.associateBy { it.recordingItemId }
        for ((recId, callId) in ownCall) {
            assertThat(byRecording[recId]?.callItemId).isEqualTo(callId)
        }
    }

    private fun recording(id: Long, title: String, durMs: Long, modified: Long) =
        RecordingInputs.Recording(
            id = id,
            title = title,
            folder = "Recordings/Call",
            primaryTs = modified,
            dates = mapOf(DateKind.MODIFIED to modified),
            durationMs = durMs,
            sizeBytes = 1000,
        )

    private companion object {
        const val CALLS = 50_000
        const val PARSED = 5_000
        const val UNPARSED = 500
    }
}
