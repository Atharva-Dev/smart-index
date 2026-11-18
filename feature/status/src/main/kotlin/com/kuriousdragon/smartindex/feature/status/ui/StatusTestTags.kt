package com.kuriousdragon.smartindex.feature.status.ui

import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.ItemType

/** Test tags of the Index status screen (LLD §4.7); never use string literals in tests. */
object StatusTestTags {
    const val BACK = "sidx_status_back"
    const val LIST = "sidx_status_list"
    const val BANNER = "sidx_status_banner"
    const val PERMISSIONS_HEADER = "sidx_status_permissions_header"
    const val REINDEX = "sidx_status_reindex"
    const val REINDEX_CONFIRM = "sidx_status_reindex_confirm"

    fun typeRow(type: ItemType): String = "sidx_status_type_${type.name}"

    fun typeCount(type: ItemType): String = "sidx_status_count_${type.name}"

    fun permissionRow(group: Group): String = "sidx_status_perm_${group.name}"

    fun allow(group: Group): String = "sidx_status_allow_${group.name}"
}
