package com.kuriousdragon.smartindex.feature.status.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.feature.status.R

/** Icon of an item type in the status rows (LLD §4.3). Colours come from `SmartIndexTheme.colors.types`. */
@Composable
fun typeIcon(type: ItemType): ImageVector = when (type) {
    ItemType.PHOTO -> Icons.Outlined.Image
    ItemType.VIDEO -> Icons.Outlined.Videocam
    ItemType.DOCUMENT -> Icons.Outlined.Description
    ItemType.OTHER_FILE -> Icons.AutoMirrored.Outlined.InsertDriveFile
    ItemType.CALL -> Icons.Outlined.Call
    ItemType.RECORDING -> Icons.Outlined.Mic
    ItemType.MESSAGE -> Icons.Outlined.Sms
}

/** Icon of a permission group (LLD §4.3). */
@Composable
fun groupIcon(group: Group): ImageVector = when (group) {
    Group.PHOTOS_VIDEOS -> Icons.Outlined.PhotoLibrary
    Group.FILES -> Icons.Outlined.FolderOpen
    Group.MESSAGES -> Icons.Outlined.Sms
    Group.CALL_LOG -> Icons.Outlined.Call
    Group.CONTACTS -> Icons.Outlined.Contacts
}

@StringRes
internal fun typeNameRes(type: ItemType): Int = when (type) {
    ItemType.PHOTO -> R.string.type_photos
    ItemType.VIDEO -> R.string.type_videos
    ItemType.DOCUMENT -> R.string.type_documents
    ItemType.OTHER_FILE -> R.string.type_other_files
    ItemType.CALL -> R.string.type_calls
    ItemType.RECORDING -> R.string.type_recordings
    ItemType.MESSAGE -> R.string.type_messages
}

@StringRes
internal fun groupNameRes(group: Group): Int = when (group) {
    Group.PHOTOS_VIDEOS -> R.string.group_photos
    Group.FILES -> R.string.group_files
    Group.MESSAGES -> R.string.group_messages
    Group.CALL_LOG -> R.string.group_calls
    Group.CONTACTS -> R.string.group_contacts
}
