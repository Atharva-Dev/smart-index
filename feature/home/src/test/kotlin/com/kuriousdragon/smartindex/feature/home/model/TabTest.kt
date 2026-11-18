package com.kuriousdragon.smartindex.feature.home.model

import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.ItemType
import org.junit.Test

class TabTest {
    @Test
    fun `tabs are declared in display order`() {
        assertThat(Tab.entries.map { it.name }).containsExactly(
            "ALL",
            "PHOTOS",
            "VIDEOS",
            "DOCUMENTS",
            "OTHER_FILES",
            "CALLS",
            "RECORDINGS",
            "MESSAGES",
        ).inOrder()
    }

    @Test
    fun `tags are derived from the item type`() {
        assertThat(Tab.ALL.tag).isEqualTo("sidx_tab_ALL")
        for (tab in Tab.entries.filter { it.type != null }) {
            assertThat(tab.tag).isEqualTo("sidx_tab_" + tab.type!!.name)
        }
        assertThat(Tab.entries.map { it.tag }.toSet()).hasSize(Tab.entries.size)
    }

    @Test
    fun `each tab maps to the permission group that unlocks it`() {
        assertThat(Tab.ALL.group).isNull()
        assertThat(Tab.PHOTOS.group).isEqualTo(Group.PHOTOS_VIDEOS)
        assertThat(Tab.VIDEOS.group).isEqualTo(Group.PHOTOS_VIDEOS)
        assertThat(Tab.DOCUMENTS.group).isEqualTo(Group.FILES)
        assertThat(Tab.OTHER_FILES.group).isEqualTo(Group.FILES)
        assertThat(Tab.RECORDINGS.group).isEqualTo(Group.FILES)
        assertThat(Tab.CALLS.group).isEqualTo(Group.CALL_LOG)
        assertThat(Tab.MESSAGES.group).isEqualTo(Group.MESSAGES)
    }

    @Test
    fun `every item type has exactly one tab`() {
        assertThat(Tab.entries.mapNotNull { it.type }).containsExactlyElementsIn(ItemType.entries)
    }
}
