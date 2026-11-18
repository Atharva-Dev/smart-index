package com.kuriousdragon.smartindex.core.index.debug

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.index.IndexDbTest
import com.kuriousdragon.smartindex.core.model.SiLog
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class StorageReporterTest : IndexDbTest() {
    private val lines = mutableListOf<Triple<Int, String, String>>()
    private val oldSink = SiLog.sink
    private val oldVerbose = SiLog.verbose

    @Before
    fun capture() {
        SiLog.verbose = true
        SiLog.sink =
            SiLog.Sink { priority, tag, message -> synchronized(lines) { lines += Triple(priority, tag, message) } }
    }

    private fun reporterLines() = synchronized(lines) { lines.filter { it.second == "SI.Index" } }

    @After
    fun restore() {
        SiLog.sink = oldSink
        SiLog.verbose = oldVerbose
    }

    @Test
    fun `reporting on an in-memory database never throws and logs sizes`() {
        runBlocking { writer.upsert(listOf(sms(1), photo(2))) }
        synchronized(lines) { lines.clear() }
        runBlocking { StorageReporter(db).reportIfDebug() }
        val line = reporterLines().single { it.third.startsWith("db=") }.third
        assertThat(line).startsWith("db=")
        assertThat(line).contains("wal=0.0")
        assertThat(line).contains("items=2")
    }

    @Test
    fun `reporting on a closed database logs a warning with the class name and does not throw`() {
        db.close()
        synchronized(lines) { lines.clear() }
        runBlocking { StorageReporter(db).reportIfDebug() }
        val warn = reporterLines().single { it.first == android.util.Log.WARN }
        assertThat(warn.third).contains("class=")
    }

    @Test
    fun `an upsert logs one line per chunk without content`() {
        runBlocking { writer.upsert(listOf(sms(1), sms(2))) }
        runBlocking { writer.upsert(listOf(sms(1), sms(3))) }
        val chunks = synchronized(lines) { lines.map { it.third } }.filter { it.startsWith("n=") }
        assertThat(chunks).hasSize(2)
        assertThat(chunks[0]).startsWith("n=2 written=2 skipped=0 ms=")
        assertThat(chunks[1]).startsWith("n=2 written=1 skipped=1 ms=")
    }
}
