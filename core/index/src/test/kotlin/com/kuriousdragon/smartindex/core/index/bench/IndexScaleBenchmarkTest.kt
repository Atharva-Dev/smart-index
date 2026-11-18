package com.kuriousdragon.smartindex.core.index.bench

import android.content.Context
import androidx.paging.testing.asSnapshot
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.index.TestE164
import com.kuriousdragon.smartindex.core.index.db.IndexDatabase
import com.kuriousdragon.smartindex.core.index.debug.StorageReporter
import com.kuriousdragon.smartindex.core.index.query.IndexChangeSignal
import com.kuriousdragon.smartindex.core.index.query.RoomIndexQuery
import com.kuriousdragon.smartindex.core.index.query.TimeZoneSource
import com.kuriousdragon.smartindex.core.index.write.RoomIndexWriter
import com.kuriousdragon.smartindex.core.model.DateRange
import com.kuriousdragon.smartindex.core.model.DefaultPhoneNumberNormalizer
import com.kuriousdragon.smartindex.core.model.ResultItem
import com.kuriousdragon.smartindex.core.model.SearchQuery
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Opt-in JVM scale benchmark (LLD §7.4): `./gradlew :core:index:test --tests '*IndexScaleBenchmarkTest'
 * -Psidx.bench=true -Psidx.bench.items=200000`. Skipped unless `sidx.bench` is "true". Asserts only that the
 * planted items are found and that the desktop p95 stays under one second for every path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IndexScaleBenchmarkTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private class Row(val path: String, val p50: Long, val p95: Long)

    private val rows = ArrayList<Row>()

    @Test
    fun `scale benchmark finds the planted items and stays under one second`() {
        Assume.assumeTrue(System.getProperty("sidx.bench") == "true")
        val items = System.getProperty("sidx.bench.items")?.toIntOrNull() ?: DEFAULT_ITEMS
        val data = BenchData(items)
        val file = File(tmp.newFolder(), "bench-index.db")
        val db = IndexDatabase.build(ApplicationProvider.getApplicationContext<Context>(), file.absolutePath)
        try {
            val numbers = DefaultPhoneNumberNormalizer(country = { "IN" }, e164 = TestE164)
            val writer = RoomIndexWriter(db, numbers, StorageReporter(db))
            val query = RoomIndexQuery(db, IndexChangeSignal(db), TimeZoneSource { BenchData.ZONE })
            runBlocking {
                val loadStart = System.nanoTime()
                writer.setContactNames(data.contactNames)
                data.batches().forEach { writer.upsert(it) }
                println("BENCH loaded items=$items in ${(System.nanoTime() - loadStart) / NANOS_PER_MS} ms")
            }
            runBenchmark(data, query)
            printReport(items, file)
        } finally {
            db.close()
        }
    }

    private fun runBenchmark(data: BenchData, query: RoomIndexQuery) {
        val day = SearchQuery("", null, null)
        val week = DateRange(data.rangeStart7Days, data.rangeEnd)

        // Day timeline: the planted day holds the planted items.
        val timeline = measure("day timeline") { first(query.timeline(data.plantDay, null)) }
        assertThat(timeline.size).isAtLeast(data.seaViewSms + data.seaViewDocs)

        val seaView = measure("sea view") { first(query.search(day.copy(text = "sea view"))) }
        assertThat(seaView).hasSize(data.seaViewSms + data.seaViewDocs)
        assertThat(seaView.all { it.matches.isNotEmpty() }).isTrue()

        assertThat(measure("rahul") { first(query.search(day.copy(text = "rahul"))) }).isNotEmpty()
        assertThat(measure("payment") { first(query.search(day.copy(text = "payment"))) }).isNotEmpty()
        assertThat(measure("the") { first(query.search(day.copy(text = "the"))) }).isNotEmpty()
        val hotel = measure("होटल") { first(query.search(day.copy(text = "होटल"))) }
        assertThat(hotel).isNotEmpty()
        assertThat(measure("payment + 7-day range") { first(query.search(day.copy(text = "payment", range = week))) })
            .isNotEmpty()
        assertThat(measure("ra") { first(query.search(day.copy(text = "ra"))) }).isNotEmpty()
        assertThat(measure("qz") { first(query.search(day.copy(text = "qz"))) }).hasSize(1)
        assertThat(measure("x") { first(query.search(day.copy(text = "x"))) }).hasSize(1)

        val payment = measure("counts payment") { runBlocking { query.counts(day.copy(text = "payment")) } }
        assertThat(payment.values.sum()).isGreaterThan(0)
        val ra = measure("counts ra") { runBlocking { query.counts(day.copy(text = "ra")) } }
        assertThat(ra.values.sum()).isGreaterThan(0)

        for (row in rows) {
            assertThat(row.p95).isLessThan(P95_LIMIT_MS)
        }
    }

    private fun first(flow: kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<ResultItem>>): List<ResultItem> =
        runBlocking { flow.asSnapshot { } }

    private fun <T> measure(path: String, block: () -> T): T {
        val times = ArrayList<Long>(RUNS)
        var last: T? = null
        repeat(RUNS) {
            val t0 = System.nanoTime()
            last = block()
            times += (System.nanoTime() - t0) / NANOS_PER_MS
        }
        times.sort()
        rows += Row(path, times[(RUNS - 1) / 2], times[Math.ceil(RUNS * 0.95).toInt() - 1])
        @Suppress("UNCHECKED_CAST")
        return last as T
    }

    private fun printReport(items: Int, file: File) {
        val sb = StringBuilder("\nBENCH items=$items runs=$RUNS (ms)\n")
        sb.append(String.format(Locale.ROOT, "%-24s %8s %8s %12s%n", "path", "p50", "p95", "phone x4 p95"))
        for (r in rows) {
            sb.append(String.format(Locale.ROOT, "%-24s %8d %8d %12d%n", r.path, r.p50, r.p95, r.p95 * PHONE_FACTOR))
        }
        val wal = File(file.path + "-wal")
        sb.append(String.format(Locale.ROOT, "DB size %.1f MB, WAL %.1f MB%n", mb(file.length()), mb(wal.length())))
        println(sb)
    }

    private fun mb(bytes: Long) = bytes / BYTES_PER_MB

    private companion object {
        const val DEFAULT_ITEMS = 50_000
        const val RUNS = 15
        const val P95_LIMIT_MS = 1_000L
        const val PHONE_FACTOR = 4
        const val NANOS_PER_MS = 1_000_000L
        const val BYTES_PER_MB = 1_048_576.0
    }
}
