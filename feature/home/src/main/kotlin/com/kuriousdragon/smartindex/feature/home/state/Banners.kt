package com.kuriousdragon.smartindex.feature.home.state

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.IndexStatus
import com.kuriousdragon.smartindex.feature.home.R
import com.kuriousdragon.smartindex.feature.home.model.Tab
import java.util.Locale

/** [total] == 0: unknown. */
@Immutable
data class IndexingBanner(val done: Int, val total: Int)

/** The denied groups, in [PERMISSION_BANNER_ORDER]. */
@Immutable
data class PermissionBanner(val groups: List<Group>)

@Immutable
data class BannersUi(val indexing: IndexingBanner?, val permission: PermissionBanner?)

/** Fixed order of the groups named by the permission banner (A-H2). Contacts is never listed. */
internal val PERMISSION_BANNER_ORDER: List<Group> =
    listOf(Group.PHOTOS_VIDEOS, Group.FILES, Group.CALL_LOG, Group.MESSAGES)

/**
 * Indexing banner only while a FULL run is active. Permission banner only on the ALL tab, only when at
 * least one group is DENIED or DENIED_PERMANENTLY (PARTIAL is not denied), and not while snoozed.
 */
internal fun buildBanners(
    status: IndexStatus,
    groups: Map<Group, GroupState>,
    tab: Tab,
    snoozedUntilMs: Long,
    nowMs: Long,
): BannersUi {
    val indexing = if (status.isIndexing) IndexingBanner(status.done, status.total) else null
    val denied = PERMISSION_BANNER_ORDER.filter {
        groups[it] == GroupState.DENIED || groups[it] == GroupState.DENIED_PERMANENTLY
    }
    val permission = if (tab == Tab.ALL && nowMs >= snoozedUntilMs && denied.isNotEmpty()) {
        PermissionBanner(denied)
    } else {
        null
    }
    return BannersUi(indexing, permission)
}

@StringRes
internal fun Group.permissionTypePhraseRes(): Int = when (this) {
    Group.PHOTOS_VIDEOS -> R.string.perm_type_photos_videos
    Group.FILES -> R.string.perm_type_files
    Group.CALL_LOG -> R.string.perm_type_calls
    Group.MESSAGES -> R.string.perm_type_messages
    Group.CONTACTS -> error("Contacts is never named by the permission banner")
}

@StringRes
internal fun Group.permissionSourcePhraseRes(): Int = when (this) {
    Group.PHOTOS_VIDEOS -> R.string.perm_source_photos_videos
    Group.FILES -> R.string.perm_source_files
    Group.CALL_LOG -> R.string.perm_source_calls
    Group.MESSAGES -> R.string.perm_source_messages
    Group.CONTACTS -> error("Contacts is never named by the permission banner")
}

/** One item: X; two: X and Y; three or more: X, Y and Z. */
internal fun joinPhrases(items: List<String>, resources: Resources): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> resources.getString(R.string.list_and, items.dropLast(1).joinToString(", "), items.last())
}

/** Title: the capitalised type phrases joined, plus "are hidden". */
internal fun PermissionBanner.title(resources: Resources): String {
    val types = joinPhrases(groups.map { resources.getString(it.permissionTypePhraseRes()) }, resources)
    val capitalised = types.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    return resources.getString(R.string.banner_perm_title, capitalised)
}

/** Body: "Smart Index can't read <sources> yet. Allow access to include them." */
internal fun PermissionBanner.body(resources: Resources): String {
    val sources = joinPhrases(groups.map { resources.getString(it.permissionSourcePhraseRes()) }, resources)
    return resources.getString(R.string.banner_perm_body, sources)
}
