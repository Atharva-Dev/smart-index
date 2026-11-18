package com.kuriousdragon.smartindex.tools.seeder.wipe

import java.io.File

/** One created row: `kind` is contact, call, sms, mms or file; `uri` its content URI (LLD §4.10). */
data class LedgerEntry(val kind: String, val uri: String)

/**
 * Append-only record of everything a run wrote: `files/ledger/<runId>.tsv`, lines `kind\turi`, buffered and
 * flushed every [flushEvery] rows so a process death keeps all but the last partial batch.
 */
class Ledger(private val dir: File, val runId: String, private val flushEvery: Int = FLUSH_EVERY) {
    private val file = File(dir, "$runId.tsv")
    private val buffer = ArrayList<String>(flushEvery)

    @Synchronized
    fun append(kind: String, uri: String) {
        require(kind.none { it == '\t' || it == '\n' } && uri.none { it == '\t' || it == '\n' }) { "bad ledger field" }
        buffer += "$kind\t$uri"
        if (buffer.size >= flushEvery) flush()
    }

    @Synchronized
    fun flush() {
        if (buffer.isEmpty()) return
        dir.mkdirs()
        file.appendText(buffer.joinToString(separator = "\n", postfix = "\n"))
        buffer.clear()
    }

    /** Rows of this run already flushed to disk. */
    fun readAll(): List<LedgerEntry> = readFile(file)

    companion object {
        const val FLUSH_EVERY = 500

        /** Rows of every ledger file in [dir] (all runs). */
        fun readAllRuns(dir: File): List<LedgerEntry> =
            dir.listFiles { f -> f.extension == "tsv" }.orEmpty().sortedBy { it.name }.flatMap { readFile(it) }

        private fun readFile(file: File): List<LedgerEntry> {
            if (!file.exists()) return emptyList()
            return file.readLines().mapNotNull { line ->
                val i = line.indexOf('\t')
                if (i <= 0 || i == line.lastIndex) null else LedgerEntry(line.substring(0, i), line.substring(i + 1))
            }
        }
    }
}
