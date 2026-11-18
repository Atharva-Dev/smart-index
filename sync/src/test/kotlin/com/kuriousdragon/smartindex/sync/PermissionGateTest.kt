package com.kuriousdragon.smartindex.sync

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.PermissionService
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SyncState
import com.kuriousdragon.smartindex.sync.fakes.FakeIndexWriter
import com.kuriousdragon.smartindex.sync.fakes.FakeSyncPrefs
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PermissionGateTest {
    private val all = Group.entries.toSet()
    private val states = MutableStateFlow(stateOf(all))
    private val permissions = mockk<PermissionService> { every { state } returns states }
    private val writer = FakeIndexWriter()
    private val prefs = FakeSyncPrefs()
    private val gate = PermissionGate(permissions, writer, prefs)

    private fun stateOf(usable: Set<Group>, partial: Set<Group> = emptySet()): Map<Group, GroupState> =
        Group.entries.associateWith {
            when (it) {
                in partial -> GroupState.PARTIAL
                in usable -> GroupState.GRANTED
                else -> GroupState.DENIED
            }
        }

    private fun resetSources() = writer.saves.map { it.source }.toSet()

    @Test
    fun `first run resets every usable source and reports changed`() = runTest {
        val result = gate.prepare()
        assertThat(result.changed).isTrue()
        assertThat(result.usable).isEqualTo(all)
        assertThat(resetSources()).isEqualTo(Source.entries.toSet())
        assertThat(writer.purges).isEmpty()
        assertThat(prefs.grantedGroups).isEqualTo(all)
    }

    @Test
    fun `first run with only calls granted resets only the call log`() = runTest {
        states.value = stateOf(setOf(Group.CALL_LOG))
        val result = gate.prepare()
        assertThat(result.changed).isTrue()
        assertThat(resetSources()).containsExactly(Source.CALL_LOG)
        assertThat(prefs.grantedGroups).containsExactly(Group.CALL_LOG)
    }

    @Test
    fun `revoking messages purges sms and mms and resets both`() = runTest {
        prefs.grantedGroups = all
        states.value = stateOf(all - Group.MESSAGES)
        val result = gate.prepare()
        assertThat(result.changed).isTrue()
        assertThat(writer.purges).containsExactly(Source.SMS to null, Source.MMS to null)
        assertThat(resetSources()).containsExactly(Source.SMS, Source.MMS)
        assertThat(prefs.grantedGroups).isEqualTo(all - Group.MESSAGES)
    }

    @Test
    fun `revoking photos and videos purges only those types and resets media store`() = runTest {
        prefs.grantedGroups = all
        states.value = stateOf(all - Group.PHOTOS_VIDEOS)
        gate.prepare()
        assertThat(writer.purges)
            .containsExactly(Source.MEDIA_STORE to setOf(ItemType.PHOTO, ItemType.VIDEO))
        assertThat(resetSources()).containsExactly(Source.MEDIA_STORE)
    }

    @Test
    fun `revoking files purges documents other files and recordings`() = runTest {
        prefs.grantedGroups = all
        states.value = stateOf(all - Group.FILES)
        gate.prepare()
        assertThat(writer.purges).containsExactly(
            Source.MEDIA_STORE to setOf(ItemType.DOCUMENT, ItemType.OTHER_FILE, ItemType.RECORDING),
        )
        assertThat(resetSources()).containsExactly(Source.MEDIA_STORE)
    }

    @Test
    fun `granting call log resets it without purging`() = runTest {
        prefs.grantedGroups = all - Group.CALL_LOG
        val result = gate.prepare()
        assertThat(result.changed).isTrue()
        assertThat(writer.purges).isEmpty()
        assertThat(resetSources()).containsExactly(Source.CALL_LOG)
        assertThat(prefs.grantedGroups).isEqualTo(all)
    }

    @Test
    fun `granting contacts changes nothing but the ledger`() = runTest {
        prefs.grantedGroups = all - Group.CONTACTS
        val result = gate.prepare()
        assertThat(result.changed).isTrue()
        assertThat(writer.purges).isEmpty()
        assertThat(writer.saves).isEmpty()
        assertThat(prefs.grantedGroups).isEqualTo(all)
    }

    @Test
    fun `revoking contacts changes nothing but the ledger`() = runTest {
        prefs.grantedGroups = all
        states.value = stateOf(all - Group.CONTACTS)
        val result = gate.prepare()
        assertThat(result.changed).isTrue()
        assertThat(writer.purges).isEmpty()
        assertThat(writer.saves).isEmpty()
        assertThat(prefs.grantedGroups).isEqualTo(all - Group.CONTACTS)
    }

    @Test
    fun `re-index resets all four sources keeps rows and clears the flag`() = runTest {
        prefs.grantedGroups = all
        prefs.reindexRequested = true
        val result = gate.prepare()
        assertThat(result.changed).isFalse()
        assertThat(writer.purges).isEmpty()
        assertThat(writer.saves).hasSize(4)
        assertThat(resetSources()).isEqualTo(Source.entries.toSet())
        assertThat(prefs.reindexRequested).isFalse()
    }

    @Test
    fun `a throwing purge keeps the ledger, still purges the other source and retries next time`() = runTest {
        prefs.grantedGroups = all
        states.value = stateOf(all - Group.MESSAGES)
        writer.throwOnPurge = setOf(Source.SMS)
        gate.prepare()
        assertThat(writer.purges).containsExactly(Source.SMS to null, Source.MMS to null).inOrder()
        assertThat(prefs.grantedGroups).isEqualTo(all)

        writer.purges.clear()
        writer.throwOnPurge = emptySet()
        gate.prepare()
        assertThat(writer.purges).containsExactly(Source.SMS to null, Source.MMS to null)
        assertThat(prefs.grantedGroups).isEqualTo(all - Group.MESSAGES)
    }

    @Test
    fun `partial access counts as usable`() = runTest {
        prefs.grantedGroups = all
        states.value = stateOf(all, partial = setOf(Group.PHOTOS_VIDEOS))
        val result = gate.prepare()
        assertThat(result.usable).isEqualTo(all)
        assertThat(result.changed).isFalse()
        assertThat(writer.purges).isEmpty()
        assertThat(writer.saves).isEmpty()
    }

    @Test
    fun `no change means no writes and changed false`() = runTest {
        prefs.grantedGroups = all
        val result = gate.prepare()
        assertThat(result.changed).isFalse()
        assertThat(writer.purges).isEmpty()
        assertThat(writer.saves).isEmpty()
        assertThat(prefs.grantedGroups).isEqualTo(all)
    }

    @Test
    fun `reset preserves lastRun and clears cursor, version, reconcile and error`() = runTest {
        writer.states[Source.SMS] = SyncState(Source.SMS, 42, "v9", 1000L, 2000L, "boom")
        prefs.grantedGroups = all - Group.MESSAGES
        gate.prepare()
        assertThat(writer.states[Source.SMS]).isEqualTo(SyncState(Source.SMS, 0, null, null, 2000L, null))
    }

    @Test
    fun `a failing state save propagates and leaves the ledger and flag untouched`() = runTest {
        prefs.reindexRequested = true
        writer.throwOnSave = true
        val failure = runCatching { gate.prepare() }.exceptionOrNull()
        assertThat(failure).isInstanceOf(IllegalStateException::class.java)
        assertThat(prefs.grantedGroups).isNull()
        assertThat(prefs.reindexRequested).isTrue()
    }
}
