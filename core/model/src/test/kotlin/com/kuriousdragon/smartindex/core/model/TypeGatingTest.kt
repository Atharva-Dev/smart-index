package com.kuriousdragon.smartindex.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TypeGatingTest {
    @Test
    fun `every item type maps to its group`() {
        assertThat(TypeGating.groupOf(ItemType.PHOTO)).isEqualTo(Group.PHOTOS_VIDEOS)
        assertThat(TypeGating.groupOf(ItemType.VIDEO)).isEqualTo(Group.PHOTOS_VIDEOS)
        assertThat(TypeGating.groupOf(ItemType.DOCUMENT)).isEqualTo(Group.FILES)
        assertThat(TypeGating.groupOf(ItemType.OTHER_FILE)).isEqualTo(Group.FILES)
        assertThat(TypeGating.groupOf(ItemType.RECORDING)).isEqualTo(Group.FILES)
        assertThat(TypeGating.groupOf(ItemType.CALL)).isEqualTo(Group.CALL_LOG)
        assertThat(TypeGating.groupOf(ItemType.MESSAGE)).isEqualTo(Group.MESSAGES)
    }

    @Test
    fun `contacts allows no item types and no sources`() {
        assertThat(TypeGating.typesOf(Group.CONTACTS)).isEmpty()
        assertThat(TypeGating.sourcesOf(Group.CONTACTS)).isEmpty()
    }

    @Test
    fun `types of a group are exactly the types mapped to it`() {
        assertThat(TypeGating.typesOf(Group.PHOTOS_VIDEOS)).containsExactly(ItemType.PHOTO, ItemType.VIDEO)
        assertThat(TypeGating.typesOf(Group.FILES))
            .containsExactly(ItemType.DOCUMENT, ItemType.OTHER_FILE, ItemType.RECORDING)
    }

    @Test
    fun `media store is allowed by both media groups`() {
        assertThat(TypeGating.groupsOf(Source.MEDIA_STORE)).containsExactly(Group.PHOTOS_VIDEOS, Group.FILES)
        assertThat(TypeGating.groupsOf(Source.CALL_LOG)).containsExactly(Group.CALL_LOG)
        assertThat(TypeGating.groupsOf(Source.SMS)).containsExactly(Group.MESSAGES)
        assertThat(TypeGating.groupsOf(Source.MMS)).containsExactly(Group.MESSAGES)
    }

    @Test
    fun `sources of a group are the sources it can allow`() {
        assertThat(TypeGating.sourcesOf(Group.MESSAGES)).containsExactly(Source.SMS, Source.MMS)
        assertThat(TypeGating.sourcesOf(Group.FILES)).containsExactly(Source.MEDIA_STORE)
    }

    @Test
    fun `usable groups keep granted and partial only`() {
        val usable = TypeGating.usableGroups(
            mapOf(
                Group.PHOTOS_VIDEOS to GroupState.PARTIAL,
                Group.FILES to GroupState.GRANTED,
                Group.MESSAGES to GroupState.DENIED,
                Group.CALL_LOG to GroupState.DENIED_PERMANENTLY,
            ),
        )
        assertThat(usable).containsExactly(Group.PHOTOS_VIDEOS, Group.FILES)
        assertThat(TypeGating.isUsable(null as GroupState?)).isFalse()
    }

    @Test
    fun `a source is usable when any group that allows it is usable`() {
        assertThat(TypeGating.isUsable(Source.MEDIA_STORE, setOf(Group.FILES))).isTrue()
        assertThat(TypeGating.isUsable(Source.MEDIA_STORE, setOf(Group.CALL_LOG))).isFalse()
        assertThat(TypeGating.isUsable(Source.SMS, emptySet())).isFalse()
    }

    @Test
    fun `usable types follow the usable groups`() {
        assertThat(TypeGating.usableTypes(setOf(Group.CALL_LOG, Group.CONTACTS))).containsExactly(ItemType.CALL)
        assertThat(TypeGating.usableTypes(emptySet())).isEmpty()
    }
}
