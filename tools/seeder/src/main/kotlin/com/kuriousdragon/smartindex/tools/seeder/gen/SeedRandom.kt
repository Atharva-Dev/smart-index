package com.kuriousdragon.smartindex.tools.seeder.gen

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * SplitMix64 (LLD §4.3), stable forever: the generated data depends on its exact output. Never replace it by
 * `kotlin.random.Random` or `java.util.Random` (their algorithms are not guaranteed stable).
 */
class SeedRandom(seed: Long) {
    private var state: Long = seed

    fun nextLong(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX_1
        z = (z xor (z ushr 27)) * MIX_2
        return z xor (z ushr 31)
    }

    /** Uniform in [0, bound) by rejection on 31 random bits. */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive: $bound" }
        val limit = TWO_POW_31 - (TWO_POW_31 % bound)
        while (true) {
            val r = nextLong() ushr 33
            if (r < limit) return (r % bound).toInt()
        }
    }

    /** Uniform in [from, to] (both inclusive). */
    fun nextInt(from: Int, to: Int): Int {
        require(to >= from) { "empty range $from..$to" }
        return from + nextInt(to - from + 1)
    }

    /** Uniform in [0, bound) for 64-bit bounds, by rejection. */
    fun nextLong(bound: Long): Long {
        require(bound > 0) { "bound must be positive: $bound" }
        val limit = Long.MAX_VALUE - (Long.MAX_VALUE % bound)
        while (true) {
            val r = nextLong() ushr 1
            if (r < limit) return r % bound
        }
    }

    /** Uniform in [0, 1) with 53 random bits. */
    fun nextDouble(): Double = (nextLong() ushr 11) * TWO_POW_MINUS_53

    /** Standard normal (Box–Muller, one value per two draws, nothing cached). */
    fun nextGaussian(): Double {
        val u1 = 1.0 - nextDouble()
        val u2 = nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }

    fun chance(p: Double): Boolean = nextDouble() < p

    fun <T> pick(items: List<T>): T = items[nextInt(items.size)]

    /** Lognormal with the given median; sigma is the standard deviation of the underlying normal. */
    fun lognormal(median: Double, sigma: Double): Double = median * kotlin.math.exp(sigma * nextGaussian())

    /** Fisher–Yates shuffle in place. */
    fun <T> shuffle(items: MutableList<T>) {
        for (i in items.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            val t = items[i]
            items[i] = items[j]
            items[j] = t
        }
    }

    private companion object {
        val GOLDEN_GAMMA = 0x9E3779B97F4A7C15uL.toLong()
        val MIX_1 = 0xBF58476D1CE4E5B9uL.toLong()
        val MIX_2 = 0x94D049BB133111EBuL.toLong()
        const val TWO_POW_31 = 1L shl 31
        const val TWO_POW_MINUS_53 = 1.0 / (1L shl 53)
    }
}
