package com.kuriousdragon.smartindex.tools.seeder.wipe

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WiperPlanTest {
    private fun sms(range: IntRange) = range.map { LedgerEntry("sms", "content://sms/$it") }

    @Test
    fun `ids are chunked to at most 500 per base uri`() {
        val steps = WipePlan.plan(sms(1..1203), emptySet())
        assertThat(steps.map { it.ids.size }).containsExactly(500, 500, 203).inOrder()
        assertThat(steps.all { it.baseUri == "content://sms" && it.kind == "sms" }).isTrue()
        assertThat(steps.flatMap { it.ids }).containsExactlyElementsIn((1L..1203L).toList())
    }

    @Test
    fun `keepSmsIds are excluded`() {
        val steps = WipePlan.plan(sms(1..10), setOf(3L, 7L))
        assertThat(steps.single().ids).containsExactly(1L, 2L, 4L, 5L, 6L, 8L, 9L, 10L)
    }

    @Test
    fun `each authority and path gets its own steps`() {
        val entries = listOf(
            LedgerEntry("call", "content://call_log/calls/1"),
            LedgerEntry("file", "content://media/external/file/2"),
            LedgerEntry("contact", "content://com.android.contacts/raw_contacts/3"),
            LedgerEntry("mms", "content://mms/4"),
        )
        val steps = WipePlan.plan(entries, emptySet())
        assertThat(steps.map { it.baseUri }).containsExactly(
            "content://call_log/calls",
            "content://media/external/file",
            "content://com.android.contacts/raw_contacts",
            "content://mms",
        )
    }

    @Test
    fun `sms and mms are dropped when the role is not held`() {
        val entries =
            sms(1..3) + LedgerEntry("mms", "content://mms/9") + LedgerEntry("call", "content://call_log/calls/1")
        val steps = WipePlan.plan(entries, emptySet(), includeSmsMms = false)
        assertThat(steps.map { it.kind }).containsExactly("call")
    }

    @Test
    fun `duplicates and malformed rows are ignored`() {
        val entries = listOf(
            LedgerEntry("sms", "content://sms/1"),
            LedgerEntry("sms", "content://sms/1"),
            LedgerEntry("sms", "content://sms/notanid"),
            LedgerEntry("sms", "garbage"),
        )
        assertThat(WipePlan.plan(entries, emptySet()).single().ids).containsExactly(1L)
    }
}
