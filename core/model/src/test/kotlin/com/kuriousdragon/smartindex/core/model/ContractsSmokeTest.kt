package com.kuriousdragon.smartindex.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Smoke test of the skeleton's contract files: they compile together and keep their HLD §6 shapes. */
class ContractsSmokeTest {
    @Test
    fun `enums keep the HLD 6_1 entries in order`() {
        assertThat(ItemType.entries.map { it.name }).containsExactly(
            "PHOTO",
            "VIDEO",
            "DOCUMENT",
            "OTHER_FILE",
            "CALL",
            "RECORDING",
            "MESSAGE",
        ).inOrder()
        assertThat(Source.entries.map { it.name }).containsExactly("MEDIA_STORE", "CALL_LOG", "SMS", "MMS").inOrder()
        assertThat(Group.entries.map { it.name }).containsExactly(
            "PHOTOS_VIDEOS",
            "FILES",
            "MESSAGES",
            "CALL_LOG",
            "CONTACTS",
        ).inOrder()
        assertThat(GroupState.entries.map { it.name }).containsExactly(
            "GRANTED",
            "PARTIAL",
            "DENIED",
            "DENIED_PERMANENTLY",
        ).inOrder()
        assertThat(MsgKind.entries.map { it.name }).containsExactly("SMS", "MMS").inOrder()
    }

    @Test
    fun `every item type is gated by exactly one group and contacts gates none`() {
        val byGroup = ItemType.entries.groupBy { TypeGating.groupOf(it) }
        assertThat(byGroup.keys).containsExactly(Group.PHOTOS_VIDEOS, Group.FILES, Group.MESSAGES, Group.CALL_LOG)
        assertThat(TypeGating.typesOf(Group.CONTACTS)).isEmpty()
        assertThat(TypeGating.isUsable(GroupState.PARTIAL)).isTrue()
        assertThat(TypeGating.isUsable(GroupState.DENIED_PERMANENTLY)).isFalse()
    }

    @Test
    fun `item record defaults match HLD 6_1`() {
        val r = ItemRecord(
            key = SourceKey(Source.SMS, 7L),
            type = ItemType.MESSAGE,
            title = "x",
            primaryTs = 1L,
            dates = mapOf(DateKind.EVENT to 1L),
            sourceVersion = 1L,
        )
        assertThat(r.hasAttachment).isFalse()
        assertThat(r.body).isNull()
        assertThat(r.msgKind).isNull()
    }

    @Test
    fun `config constants used by several LLDs`() {
        assertThat(IndexConfig.UPSERT_BATCH_SIZE).isEqualTo(500)
        assertThat(IndexConfig.PAGE_SIZE).isEqualTo(50)
        assertThat(IndexConfig.SEARCH_DEBOUNCE_MS).isEqualTo(300L)
        assertThat(IndexConfig.PERIODIC_INTERVAL_MINUTES).isEqualTo(15L)
    }

    @Test
    fun `SiLog drops debug lines when not verbose and never appends exception messages`() {
        val lines = mutableListOf<String>()
        val oldSink = SiLog.sink
        val oldVerbose = SiLog.verbose
        try {
            SiLog.sink = SiLog.Sink { _, tag, msg -> lines += "$tag $msg" }
            SiLog.verbose = false
            SiLog.d("Test", "hidden")
            SiLog.i("Test", "hidden")
            SiLog.w("Test", "kept", IllegalStateException("secret +91 98765 43210"))
            assertThat(lines).containsExactly("SI.Test kept class=IllegalStateException")
        } finally {
            SiLog.sink = oldSink
            SiLog.verbose = oldVerbose
        }
    }
}
