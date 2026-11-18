package com.kuriousdragon.smartindex.feature.home.state

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.IndexPhase
import com.kuriousdragon.smartindex.core.model.IndexStatus
import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.feature.home.format.resources
import com.kuriousdragon.smartindex.feature.home.model.Tab
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BannersTest {
    private val granted = Group.entries.associateWith { GroupState.GRANTED }
    private val idle = IndexStatus()
    private val indexing = IndexStatus(phase = IndexPhase.INDEXING, mode = Mode.FULL, done = 4, total = 10)
    private val now = 1_000_000L

    private fun build(
        status: IndexStatus = idle,
        groups: Map<Group, GroupState> = granted,
        tab: Tab = Tab.ALL,
        snoozedUntil: Long = 0L,
    ) = buildBanners(status, groups, tab, snoozedUntil, now)

    @Test
    fun `no banner when idle and everything is granted`() {
        assertThat(build()).isEqualTo(BannersUi(null, null))
    }

    @Test
    fun `indexing banner only while a FULL run is active`() {
        assertThat(build(indexing).indexing).isEqualTo(IndexingBanner(4, 10))
        val incremental = IndexStatus(phase = IndexPhase.INDEXING, mode = Mode.INCREMENTAL, done = 1, total = 2)
        assertThat(build(incremental).indexing).isNull()
        assertThat(build(idle).indexing).isNull()
    }

    @Test
    fun `indexing banner is shown on every tab and keeps an unknown total`() {
        val unknown = indexing.copy(total = 0)
        assertThat(build(unknown, tab = Tab.CALLS).indexing).isEqualTo(IndexingBanner(4, 0))
    }

    @Test
    fun `permission banner lists denied groups in the fixed order`() {
        val groups = granted + mapOf(
            Group.MESSAGES to GroupState.DENIED,
            Group.PHOTOS_VIDEOS to GroupState.DENIED_PERMANENTLY,
            Group.CALL_LOG to GroupState.DENIED,
        )
        assertThat(build(groups = groups).permission)
            .isEqualTo(PermissionBanner(listOf(Group.PHOTOS_VIDEOS, Group.CALL_LOG, Group.MESSAGES)))
    }

    @Test
    fun `permission banner only on the ALL tab`() {
        val groups = granted + (Group.FILES to GroupState.DENIED)
        assertThat(build(groups = groups, tab = Tab.ALL).permission).isNotNull()
        assertThat(build(groups = groups, tab = Tab.PHOTOS).permission).isNull()
        assertThat(build(groups = groups, tab = Tab.DOCUMENTS).permission).isNull()
    }

    @Test
    fun `permission banner is hidden while snoozed and back when the snooze ends`() {
        val groups = granted + (Group.FILES to GroupState.DENIED)
        assertThat(build(groups = groups, snoozedUntil = now + 1).permission).isNull()
        assertThat(build(groups = groups, snoozedUntil = now).permission).isNotNull()
        assertThat(build(groups = groups, snoozedUntil = now - 1).permission).isNotNull()
    }

    @Test
    fun `contacts is ignored and partial is not denied`() {
        val contactsOnly = granted + (Group.CONTACTS to GroupState.DENIED)
        assertThat(build(groups = contactsOnly).permission).isNull()
        val partial = granted + (Group.PHOTOS_VIDEOS to GroupState.PARTIAL)
        assertThat(build(groups = partial).permission).isNull()
    }

    @Test
    fun `missing group states are not denied`() {
        assertThat(build(groups = emptyMap()).permission).isNull()
    }

    private val res = resources()

    @Test
    fun `copy for one group`() {
        val banner = PermissionBanner(listOf(Group.FILES))
        assertThat(banner.title(res)).isEqualTo("Files and recordings are hidden")
        assertThat(banner.body(res))
            .isEqualTo("Smart Index can't read your files yet. Allow access to include them.")
    }

    @Test
    fun `copy for two groups`() {
        val banner = PermissionBanner(listOf(Group.CALL_LOG, Group.MESSAGES))
        assertThat(banner.title(res)).isEqualTo("Calls and messages are hidden")
        assertThat(banner.body(res)).isEqualTo(
            "Smart Index can't read your call history and your messages yet. Allow access to include them.",
        )
    }

    @Test
    fun `copy for three groups`() {
        val banner = PermissionBanner(listOf(Group.PHOTOS_VIDEOS, Group.FILES, Group.MESSAGES))
        assertThat(banner.title(res))
            .isEqualTo("Photos and videos, files and recordings and messages are hidden")
        assertThat(banner.body(res)).isEqualTo(
            "Smart Index can't read your photos, your files and your messages yet. Allow access to include them.",
        )
    }

    @Test
    fun `copy for four groups`() {
        val banner = PermissionBanner(
            listOf(Group.PHOTOS_VIDEOS, Group.FILES, Group.CALL_LOG, Group.MESSAGES),
        )
        assertThat(banner.title(res))
            .isEqualTo("Photos and videos, files and recordings, calls and messages are hidden")
        assertThat(banner.body(res)).isEqualTo(
            "Smart Index can't read your photos, your files, your call history and your messages yet. " +
                "Allow access to include them.",
        )
    }
}
