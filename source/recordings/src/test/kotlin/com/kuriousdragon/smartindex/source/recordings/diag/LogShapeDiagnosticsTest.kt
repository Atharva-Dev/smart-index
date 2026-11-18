package com.kuriousdragon.smartindex.source.recordings.diag

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.source.recordings.match.MatchStats
import org.junit.Test

class LogShapeDiagnosticsTest {
    private val stats =
        MatchStats(
            calls = 3,
            eligibleCalls = 2,
            recordings = 4,
            parsedByPattern = mapOf("p1" to 2),
            unparsed = 2,
            emptyFiles = 0,
            noAnchor = 0,
            frozenLinks = 0,
            candidates = 5,
            contradictions = 0,
            linked = 1,
            linkedByMethod = mapOf("NUMBER" to 1),
            unlinkedNoCandidate = 0,
            unlinkedLost = 0,
            tookMs = 7,
        )

    private fun diag(lines: MutableList<String>) = LogShapeDiagnostics { lines += it }

    @Test
    fun sameShapeIsCounted() {
        val lines = mutableListOf<String>()
        val d = diag(lines)
        d.onUnparsed("Secret Rahul_241103.m4a")
        d.onUnparsed("Other Name_991231.m4a")
        d.onRunFinished(stats)
        assertThat(lines).hasSize(2)
        assertThat(lines[0]).startsWith("match calls=3")
        assertThat(lines[1]).isEqualTo("unparsed shape=\"a a_999999.m4a\" count=2")
    }

    @Test
    fun atMostFiftyDistinctShapes() {
        val lines = mutableListOf<String>()
        val d = diag(lines)
        for (n in 1..80) d.onUnparsed("x" + "9".repeat(n) + ".m4a")
        d.onUnparsed("x" + "9".repeat(1) + ".m4a")
        d.onRunFinished(stats)
        assertThat(lines.filter { it.startsWith("unparsed shape=") }).hasSize(LogShapeDiagnostics.MAX_SHAPES)
        assertThat(lines.first()).startsWith("match ")
    }

    @Test
    fun bufferClearedAfterRunFinished() {
        val lines = mutableListOf<String>()
        val d = diag(lines)
        d.onUnparsed("abc_1.m4a")
        d.onRunFinished(stats)
        lines.clear()
        d.onRunFinished(stats)
        assertThat(lines).hasSize(1)
        assertThat(lines.single()).startsWith("match ")
    }

    @Test
    fun outputNeverContainsInputNames() {
        val lines = mutableListOf<String>()
        val d = diag(lines)
        val names = listOf("Rahul Sharma_241103.m4a", "Priya +91 98765 43210.m4a")
        names.forEach(d::onUnparsed)
        d.onRunFinished(stats)
        val out = lines.joinToString("\n")
        assertThat(out).doesNotContain("Rahul")
        assertThat(out).doesNotContain("Sharma")
        assertThat(out).doesNotContain("Priya")
        assertThat(out).doesNotContain("98765")
    }
}
