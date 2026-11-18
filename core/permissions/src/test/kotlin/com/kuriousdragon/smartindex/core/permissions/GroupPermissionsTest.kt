package com.kuriousdragon.smartindex.core.permissions

import android.Manifest
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import org.junit.Test

class GroupPermissionsTest {
    @Test
    fun `photos and videos asks for images and video on sdk 33`() {
        assertThat(GroupPermissions.runtime(Group.PHOTOS_VIDEOS, 33))
            .containsExactly(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            .inOrder()
    }

    @Test
    fun `photos and videos adds visual user selected from sdk 34`() {
        assertThat(GroupPermissions.runtime(Group.PHOTOS_VIDEOS, 34))
            .containsExactly(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            ).inOrder()
    }

    @Test
    fun `files has no runtime permission`() {
        assertThat(GroupPermissions.runtime(Group.FILES, 34)).isEmpty()
    }

    @Test
    fun `single permission groups`() {
        assertThat(GroupPermissions.runtime(Group.MESSAGES, 34)).containsExactly(Manifest.permission.READ_SMS)
        assertThat(GroupPermissions.runtime(Group.CALL_LOG, 34)).containsExactly(Manifest.permission.READ_CALL_LOG)
        assertThat(GroupPermissions.runtime(Group.CONTACTS, 34)).containsExactly(Manifest.permission.READ_CONTACTS)
    }
}
