package com.kuriousdragon.smartindex.feature.status

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.IndexError
import com.kuriousdragon.smartindex.core.model.IndexPhase
import com.kuriousdragon.smartindex.core.model.IndexStats
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.feature.status.model.BannerUi
import com.kuriousdragon.smartindex.feature.status.model.PermissionAction
import com.kuriousdragon.smartindex.feature.status.model.StatusDialog
import com.kuriousdragon.smartindex.feature.status.model.StatusMapper
import com.kuriousdragon.smartindex.feature.status.model.SubLine
import com.kuriousdragon.smartindex.feature.status.model.TypeRowUi
import com.kuriousdragon.smartindex.feature.status.testing.indexStats
import com.kuriousdragon.smartindex.feature.status.testing.indexStatus
import org.junit.Test

class StatusMapperTest {
    private val allGranted = Group.entries.associateWith { GroupState.GRANTED }
    private val storageError = IndexError(IndexError.Kind.STORAGE_FULL, atMs = 1L)
    private val sourceError = IndexError(IndexError.Kind.SOURCE_FAILED, setOf(Source.SMS), atMs = 1L)

    private fun rows(
        perms: Map<Group, GroupState> = allGranted,
        stats: IndexStats? = indexStats(),
    ) = StatusMapper.typeRows(stats, perms).associateBy { it.type }

    // Banner

    @Test
    fun `FULL run with a total shows Indexing with a percent`() {
        val b = StatusMapper.banner(indexStatus(IndexPhase.INDEXING, Mode.FULL, done = 25, total = 100))
        assertThat(b).isEqualTo(BannerUi.Indexing(25, 100, 25))
    }

    @Test
    fun `FULL run without a total shows Indexing with a null percent`() {
        val b = StatusMapper.banner(indexStatus(IndexPhase.INDEXING, Mode.FULL, done = 5, total = 0))
        assertThat(b).isEqualTo(BannerUi.Indexing(5, 0, null))
    }

    @Test
    fun `INCREMENTAL run shows Updating`() {
        assertThat(StatusMapper.banner(indexStatus(IndexPhase.INDEXING, Mode.INCREMENTAL)))
            .isEqualTo(BannerUi.Updating)
    }

    @Test
    fun `PREPARING and LINKING show Updating`() {
        assertThat(StatusMapper.banner(indexStatus(IndexPhase.PREPARING, Mode.INCREMENTAL)))
            .isEqualTo(BannerUi.Updating)
        assertThat(StatusMapper.banner(indexStatus(IndexPhase.LINKING, Mode.INCREMENTAL)))
            .isEqualTo(BannerUi.Updating)
    }

    @Test
    fun `error while idle shows Error with kind and sources`() {
        assertThat(StatusMapper.banner(indexStatus(error = sourceError)))
            .isEqualTo(BannerUi.Error(IndexError.Kind.SOURCE_FAILED, setOf(Source.SMS)))
    }

    @Test
    fun `error during an INCREMENTAL run still shows Error`() {
        assertThat(StatusMapper.banner(indexStatus(IndexPhase.INDEXING, Mode.INCREMENTAL, error = storageError)))
            .isEqualTo(BannerUi.Error(IndexError.Kind.STORAGE_FULL, emptySet()))
    }

    @Test
    fun `error during a FULL run shows Indexing`() {
        val b = StatusMapper.banner(
            indexStatus(IndexPhase.INDEXING, Mode.FULL, done = 1, total = 10, error = storageError),
        )
        assertThat(b).isInstanceOf(BannerUi.Indexing::class.java)
    }

    @Test
    fun `idle with a last run shows UpToDate with it`() {
        assertThat(StatusMapper.banner(indexStatus(lastRun = 123L))).isEqualTo(BannerUi.UpToDate(123L))
    }

    @Test
    fun `idle without a last run shows UpToDate null`() {
        assertThat(StatusMapper.banner(indexStatus())).isEqualTo(BannerUi.UpToDate(null))
    }

    // Type rows

    @Test
    fun `all usable with stats gives seven rows in tab order with counts`() {
        val stats = indexStats(mapOf(ItemType.PHOTO to 10, ItemType.CALL to 3), recordingsLinked = 2)
        val list = StatusMapper.typeRows(stats, allGranted)
        assertThat(list.map { it.type }).containsExactly(
            ItemType.PHOTO,
            ItemType.VIDEO,
            ItemType.DOCUMENT,
            ItemType.OTHER_FILE,
            ItemType.CALL,
            ItemType.RECORDING,
            ItemType.MESSAGE,
        ).inOrder()
        val byType = list.associateBy { it.type }
        assertThat(byType.getValue(ItemType.PHOTO).count).isEqualTo(10)
        assertThat(byType.getValue(ItemType.CALL).count).isEqualTo(3)
        assertThat(byType.getValue(ItemType.MESSAGE).count).isEqualTo(0)
        assertThat(byType.getValue(ItemType.PHOTO).subLine).isEqualTo(SubLine.None)
    }

    @Test
    fun `null stats gives null counts and no sub-line`() {
        val list = StatusMapper.typeRows(null, allGranted)
        assertThat(list.map { it.count }.toSet()).containsExactly(null)
        assertThat(list.map { it.subLine }.toSet()).containsExactly(SubLine.None)
    }

    @Test
    fun `photos denied hides PHOTO and VIDEO only`() {
        val r = rows(allGranted + (Group.PHOTOS_VIDEOS to GroupState.DENIED))
        assertNoAccess(r, ItemType.PHOTO, ItemType.VIDEO)
    }

    @Test
    fun `files denied hides DOCUMENT OTHER_FILE and RECORDING only`() {
        val r = rows(allGranted + (Group.FILES to GroupState.DENIED_PERMANENTLY))
        assertNoAccess(r, ItemType.DOCUMENT, ItemType.OTHER_FILE, ItemType.RECORDING)
    }

    @Test
    fun `calls denied hides CALL only`() {
        assertNoAccess(rows(allGranted + (Group.CALL_LOG to GroupState.DENIED)), ItemType.CALL)
    }

    @Test
    fun `messages denied hides MESSAGE only`() {
        assertNoAccess(rows(allGranted + (Group.MESSAGES to GroupState.DENIED)), ItemType.MESSAGE)
    }

    @Test
    fun `missing group is treated as no access`() {
        assertNoAccess(rows(allGranted - Group.MESSAGES), ItemType.MESSAGE)
    }

    @Test
    fun `contacts has no type row and does not affect rows`() {
        val r = rows(allGranted + (Group.CONTACTS to GroupState.DENIED))
        assertThat(r).hasSize(7)
        assertNoAccess(r)
    }

    @Test
    fun `partial photos is usable`() {
        val r = rows(allGranted + (Group.PHOTOS_VIDEOS to GroupState.PARTIAL), indexStats(mapOf(ItemType.PHOTO to 4)))
        assertThat(r.getValue(ItemType.PHOTO).count).isEqualTo(4)
        assertThat(r.getValue(ItemType.PHOTO).subLine).isEqualTo(SubLine.None)
        assertThat(r.getValue(ItemType.VIDEO).subLine).isEqualTo(SubLine.None)
    }

    @Test
    fun `document sub-line is DocumentKinds also while indexing`() {
        val s = StatusMapper.map(
            indexStatus(IndexPhase.INDEXING, Mode.FULL, 1, 10, stats = indexStats(mapOf(ItemType.DOCUMENT to 7))),
            allGranted,
            null,
        )
        val doc = s.typeRows.first { it.type == ItemType.DOCUMENT }
        assertThat(doc.subLine).isEqualTo(SubLine.DocumentKinds)
        assertThat(doc.count).isEqualTo(7)
    }

    @Test
    fun `recording sub-line carries the linked count`() {
        val r = rows(stats = indexStats(mapOf(ItemType.RECORDING to 9), recordingsLinked = 6))
        assertThat(r.getValue(ItemType.RECORDING).subLine).isEqualTo(SubLine.RecordingsLinked(6))
        assertThat(r.getValue(ItemType.RECORDING).count).isEqualTo(9)
    }

    private fun assertNoAccess(r: Map<ItemType, TypeRowUi>, vararg types: ItemType) {
        for ((type, row) in r) {
            if (type in types) {
                assertThat(row.count).isNull()
                assertThat(row.subLine).isEqualTo(SubLine.NoAccess)
            } else {
                assertThat(row.subLine).isNotEqualTo(SubLine.NoAccess)
                assertThat(row.count).isNotNull()
            }
        }
    }

    // Permission rows

    @Test
    fun `permission rows map each state to an action in Group order`() {
        val rows = StatusMapper.permissionRows(
            mapOf(
                Group.PHOTOS_VIDEOS to GroupState.PARTIAL,
                Group.FILES to GroupState.GRANTED,
                Group.MESSAGES to GroupState.DENIED,
                Group.CALL_LOG to GroupState.DENIED_PERMANENTLY,
            ),
        )
        assertThat(rows.map { it.group }).containsExactlyElementsIn(Group.entries).inOrder()
        assertThat(rows.map { it.action }).containsExactly(
            PermissionAction.ALLOW_ALL,
            PermissionAction.NONE,
            PermissionAction.ALLOW,
            PermissionAction.ALLOW,
            PermissionAction.ALLOW,
        ).inOrder()
        assertThat(rows.last().state).isEqualTo(GroupState.DENIED)
    }

    // Re-index and header

    @Test
    fun `re-index is enabled only when idle`() {
        for (phase in IndexPhase.entries) {
            val mode = if (phase == IndexPhase.IDLE) null else Mode.FULL
            val s = StatusMapper.map(indexStatus(phase, mode), allGranted, null)
            assertThat(s.reindex.enabled).isEqualTo(phase == IndexPhase.IDLE)
        }
    }

    @Test
    fun `map carries the dialog`() {
        val s = StatusMapper.map(indexStatus(), allGranted, StatusDialog.ConfirmReindex)
        assertThat(s.dialog).isEqualTo(StatusDialog.ConfirmReindex)
    }

    @Test
    fun `permissions header index is 9`() {
        assertThat(StatusMapper.permissionsHeaderIndex()).isEqualTo(9)
    }
}
