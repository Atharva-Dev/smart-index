package com.kuriousdragon.smartindex.source.recordings.match

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GreedyAssignerTest {
    private fun cand(
        call: Long,
        rec: Long,
        bp: Int = 8000,
        delta: Long = 10_000,
        diff: Long = 1_000,
        copy: Boolean = false,
        existing: Boolean = false,
    ) = Candidate(call, rec, bp, "name+ts", delta, diff, copy, existing)

    private fun pairs(list: List<Candidate>) = list.map { it.callId to it.recordingId }

    @Test
    fun `higher score wins a contested call`() {
        val out = GreedyAssigner.assign(listOf(cand(1, 10, bp = 7000), cand(1, 11, bp = 9000)))
        assertThat(pairs(out)).containsExactly(1L to 11L)
    }

    @Test
    fun `higher score wins a contested recording`() {
        val out = GreedyAssigner.assign(listOf(cand(1, 10, bp = 7000), cand(2, 10, bp = 9000)))
        assertThat(pairs(out)).containsExactly(2L to 10L)
    }

    @Test
    fun `tie on score keeps the existing link`() {
        val out = GreedyAssigner.assign(listOf(cand(1, 10), cand(1, 11, existing = true)))
        assertThat(pairs(out)).containsExactly(1L to 11L)
    }

    @Test
    fun `tie then closer start wins`() {
        val out = GreedyAssigner.assign(listOf(cand(1, 10, delta = 20_000), cand(1, 11, delta = 5_000)))
        assertThat(pairs(out)).containsExactly(1L to 11L)
    }

    @Test
    fun `tie then closer duration wins`() {
        val out = GreedyAssigner.assign(listOf(cand(1, 10, diff = 3_000), cand(1, 11, diff = 500)))
        assertThat(pairs(out)).containsExactly(1L to 11L)
    }

    @Test
    fun `tie then the original beats the copy`() {
        val out = GreedyAssigner.assign(listOf(cand(1, 10, copy = true), cand(1, 11)))
        assertThat(pairs(out)).containsExactly(1L to 11L)
    }

    @Test
    fun `tie then lower recording id wins`() {
        val out = GreedyAssigner.assign(listOf(cand(1, 12), cand(1, 11)))
        assertThat(pairs(out)).containsExactly(1L to 11L)
    }

    @Test
    fun `tie then lower call id wins`() {
        val out = GreedyAssigner.assign(listOf(cand(2, 10), cand(1, 10)))
        assertThat(pairs(out)).containsExactly(1L to 10L)
    }

    @Test
    fun `chain resolves to the maximal greedy set`() {
        // r1 prefers c1 (9000); r2 prefers c1 (8000) but also fits c2 (6000)
        val out =
            GreedyAssigner.assign(
                listOf(cand(1, 1, bp = 9000), cand(1, 2, bp = 8000), cand(2, 2, bp = 6000)),
            )
        assertThat(pairs(out)).containsExactly(1L to 1L, 2L to 2L)
    }

    @Test
    fun `result is one-to-one and independent of input order`() {
        val input = listOf(cand(1, 1), cand(1, 2), cand(2, 1), cand(2, 2, bp = 7000), cand(3, 2, bp = 6000))
        val expected = GreedyAssigner.assign(input)
        repeat(10) { seed ->
            assertThat(GreedyAssigner.assign(input.shuffled(java.util.Random(seed.toLong()))))
                .isEqualTo(expected)
        }
        assertThat(expected.map { it.callId }.toSet()).hasSize(expected.size)
        assertThat(expected.map { it.recordingId }.toSet()).hasSize(expected.size)
    }

    @Test
    fun `empty input gives empty output`() {
        assertThat(GreedyAssigner.assign(emptyList())).isEmpty()
    }
}
