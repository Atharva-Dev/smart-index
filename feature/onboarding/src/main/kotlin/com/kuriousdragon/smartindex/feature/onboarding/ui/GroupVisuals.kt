package com.kuriousdragon.smartindex.feature.onboarding.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.kuriousdragon.smartindex.core.designsystem.theme.SmartIndexTheme
import com.kuriousdragon.smartindex.core.designsystem.theme.TypeColorKey
import com.kuriousdragon.smartindex.core.designsystem.theme.TypeColors
import com.kuriousdragon.smartindex.core.model.Group

/** Icon of a permission group (LLD §4.3). */
@Composable
fun groupIcon(group: Group): ImageVector =
    when (group) {
        Group.PHOTOS_VIDEOS -> Icons.Outlined.PhotoLibrary
        Group.FILES -> Icons.Outlined.FolderOpen
        Group.MESSAGES -> Icons.Outlined.Sms
        Group.CALL_LOG -> Icons.Outlined.Call
        Group.CONTACTS -> Icons.Outlined.Contacts
    }

/** Tile and icon colours of a permission group (LLD §4.3; contacts uses the primary container, assumption A1). */
@Composable
fun groupColors(group: Group): TypeColors {
    val types = SmartIndexTheme.colors.types
    return when (group) {
        Group.PHOTOS_VIDEOS -> types.getValue(TypeColorKey.PHOTO)

        Group.FILES -> types.getValue(TypeColorKey.DOC)

        Group.MESSAGES -> types.getValue(TypeColorKey.MSG)

        Group.CALL_LOG -> types.getValue(TypeColorKey.CALL)

        Group.CONTACTS ->
            TypeColors(
                icon = MaterialTheme.colorScheme.onPrimaryContainer,
                tile = MaterialTheme.colorScheme.primaryContainer,
            )
    }
}
