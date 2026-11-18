package com.kuriousdragon.smartindex.feature.status

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.feature.status.ui.StatusTestTags
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StatusStringsTest {
    private val ids = listOf(
        "status_title", "status_sections_items", "status_sections_permissions",
        "status_ok_title", "status_ok_body_today", "status_ok_body_yesterday", "status_ok_body_other",
        "status_never_title", "status_never_body", "status_updating_title", "status_updating_body",
        "status_indexing_title", "status_indexing_body", "status_indexing_title_unknown",
        "status_indexing_body_unknown", "status_doc_sub", "status_rec_sub", "status_noaccess",
        "status_allowed", "status_allow", "status_privacy", "status_reindex", "status_reindex_helper",
        "reindex_title", "reindex_body", "reindex_confirm", "reindex_cancel",
        "status_err_storage_title", "status_err_storage_body", "status_err_source_title",
        "status_err_source_body", "status_err_linking_title", "status_err_linking_body",
        "area_files", "area_calls", "area_messages", "area_and",
        "type_photos", "type_videos", "type_documents", "type_other_files", "type_calls",
        "type_recordings", "type_messages",
        "group_photos", "group_files", "group_messages", "group_calls", "group_contacts",
        "status_allowed_partial", "status_allow_all", "status_count_unknown",
    )

    @Test
    fun `every string id of the LLD copy table resolves to non-empty text`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (name in ids) {
            val id = context.resources.getIdentifier(name, "string", context.packageName)
            assertThat(id).isNotEqualTo(0)
            assertThat(context.getText(id).toString()).isNotEmpty()
        }
    }

    @Test
    fun `test tags follow the LLD table`() {
        assertThat(StatusTestTags.typeRow(ItemType.PHOTO)).isEqualTo("sidx_status_type_PHOTO")
        assertThat(StatusTestTags.typeCount(ItemType.CALL)).isEqualTo("sidx_status_count_CALL")
        assertThat(StatusTestTags.permissionRow(Group.FILES)).isEqualTo("sidx_status_perm_FILES")
        assertThat(StatusTestTags.allow(Group.CONTACTS)).isEqualTo("sidx_status_allow_CONTACTS")
        assertThat(StatusTestTags.BACK).isEqualTo("sidx_status_back")
    }
}
