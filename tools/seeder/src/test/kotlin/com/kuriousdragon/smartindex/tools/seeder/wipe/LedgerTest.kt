package com.kuriousdragon.smartindex.tools.seeder.wipe

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LedgerTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `entries round trip through flush and readAll`() {
        val dir = File(tmp.root, "ledger")
        val ledger = Ledger(dir, "run1")
        ledger.append("call", "content://call_log/calls/5")
        ledger.append("file", "content://media/external/file/77")
        ledger.flush()
        assertThat(File(dir, "run1.tsv").readText())
            .isEqualTo("call\tcontent://call_log/calls/5\nfile\tcontent://media/external/file/77\n")
        assertThat(ledger.readAll()).containsExactly(
            LedgerEntry("call", "content://call_log/calls/5"),
            LedgerEntry("file", "content://media/external/file/77"),
        ).inOrder()
    }

    @Test
    fun `rows are flushed automatically every 500`() {
        val dir = File(tmp.root, "ledger")
        val ledger = Ledger(dir, "run2")
        repeat(499) { ledger.append("sms", "content://sms/$it") }
        assertThat(ledger.readAll()).isEmpty()
        ledger.append("sms", "content://sms/499")
        assertThat(ledger.readAll()).hasSize(500)
        ledger.append("sms", "content://sms/500")
        assertThat(ledger.readAll()).hasSize(500)
        ledger.flush()
        assertThat(ledger.readAll()).hasSize(501)
    }

    @Test
    fun `readAllRuns merges every ledger file`() {
        val dir = File(tmp.root, "ledger")
        Ledger(dir, "a").apply {
            append("contact", "content://com.android.contacts/raw_contacts/1")
            flush()
        }
        Ledger(dir, "b").apply {
            append("call", "content://call_log/calls/2")
            flush()
        }
        assertThat(Ledger.readAllRuns(dir).map { it.kind }).containsExactly("contact", "call").inOrder()
        assertThat(Ledger.readAllRuns(File(tmp.root, "none"))).isEmpty()
    }

    @Test
    fun `tabs and newlines in fields are rejected`() {
        val ledger = Ledger(File(tmp.root, "l"), "x")
        val e = runCatching { ledger.append("sms", "content://sms/1\n2") }.exceptionOrNull()
        assertThat(e).isInstanceOf(IllegalArgumentException::class.java)
    }
}
