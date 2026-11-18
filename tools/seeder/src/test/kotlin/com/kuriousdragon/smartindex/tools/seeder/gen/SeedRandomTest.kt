package com.kuriousdragon.smartindex.tools.seeder.gen

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SeedRandomTest {
    @Test
    fun `first five values for seed 42 are frozen`() {
        val r = SeedRandom(42)
        assertThat(List(5) { r.nextLong() }).containsExactly(
            -4767286540954276203L,
            2949826092126892291L,
            5139283748462763858L,
            6349198060258255764L,
            701532786141963250L,
        ).inOrder()
    }

    @Test
    fun `same seed gives the same stream and different seeds differ`() {
        val a = SeedRandom(7)
        val b = SeedRandom(7)
        val c = SeedRandom(8)
        val sa = List(20) { a.nextLong() }
        assertThat(List(20) { b.nextLong() }).isEqualTo(sa)
        assertThat(List(20) { c.nextLong() }).isNotEqualTo(sa)
    }

    @Test
    fun `nextInt stays in range and is uniform by chi-square`() {
        val r = SeedRandom(42)
        val buckets = IntArray(10)
        repeat(10_000) {
            val v = r.nextInt(10)
            assertThat(v).isAtLeast(0)
            assertThat(v).isLessThan(10)
            buckets[v]++
        }
        val expected = 1_000.0
        val chi2 = buckets.sumOf { (it - expected) * (it - expected) / expected }
        // df = 9, p = 0.001 critical value 27.88
        assertThat(chi2).isLessThan(27.88)
    }

    @Test
    fun `nextInt with odd bounds and ranges is in range`() {
        val r = SeedRandom(1)
        repeat(5_000) {
            assertThat(r.nextInt(7)).isIn(0..6)
            assertThat(r.nextInt(3, 5)).isIn(3..5)
            assertThat(r.nextLong(1_000_000_007L)).isAtLeast(0L)
        }
    }

    @Test
    fun `nextDouble is in unit interval and gaussian is centred`() {
        val r = SeedRandom(99)
        var sum = 0.0
        var sumSq = 0.0
        val n = 20_000
        repeat(n) {
            val d = r.nextDouble()
            assertThat(d).isAtLeast(0.0)
            assertThat(d).isLessThan(1.0)
            val g = r.nextGaussian()
            sum += g
            sumSq += g * g
        }
        assertThat(sum / n).isWithin(0.05).of(0.0)
        assertThat(sumSq / n).isWithin(0.08).of(1.0)
    }
}
