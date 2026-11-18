package com.kuriousdragon.smartindex.benchmark

import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** S3: rare queries (LLD — Test & benchmark harness §3.6). */
@RunWith(Parameterized::class)
class S3RareQueryBenchmark(
    private val text: String,
    private val tabTag: String,
    private val presetTag: String,
) {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun search() = rule.measureSearch(Q(text, tabTag, presetTag.takeUnless { it == Tags.PRESET_ANY }))

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = PARAM_NAME)
        fun params(): List<Array<Any?>> = Queries.params(Queries.S3)
    }
}
