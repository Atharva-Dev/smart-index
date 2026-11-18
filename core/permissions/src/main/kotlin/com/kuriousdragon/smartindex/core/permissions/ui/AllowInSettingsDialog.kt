package com.kuriousdragon.smartindex.core.permissions.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.permissions.R

/**
 * "Allow in Settings" dialog shown when a [Group] is permanently denied (Design Spec §6.11 "Denied permanently",
 * §6.13). Never shown for [Group.FILES], whose settings page always works.
 *
 * @param onOpenSettings callers launch `service.settingsIntent(group)` here.
 */
@Composable
fun AllowInSettingsDialog(group: Group, onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    require(group != Group.FILES) { "AllowInSettingsDialog is never shown for FILES" }
    val names = group.names()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.perm_settings_title)) },
        text = {
            Text(
                stringResource(
                    R.string.perm_settings_body,
                    stringResource(names.title),
                    stringResource(names.lower),
                    stringResource(names.setting),
                ),
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.perm_settings_cancel)) }
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.perm_settings_open)) }
        },
    )
}

private class GroupNames(val title: Int, val lower: Int, val setting: Int)

private fun Group.names(): GroupNames = when (this) {
    Group.PHOTOS_VIDEOS -> GroupNames(
        R.string.perm_photos_videos_title,
        R.string.perm_photos_videos_lower,
        R.string.perm_photos_videos_setting,
    )

    Group.MESSAGES -> GroupNames(
        R.string.perm_messages_title,
        R.string.perm_messages_lower,
        R.string.perm_messages_setting,
    )

    Group.CALL_LOG -> GroupNames(
        R.string.perm_call_log_title,
        R.string.perm_call_log_lower,
        R.string.perm_call_log_setting,
    )

    Group.CONTACTS -> GroupNames(
        R.string.perm_contacts_title,
        R.string.perm_contacts_lower,
        R.string.perm_contacts_setting,
    )

    Group.FILES -> error("unreachable: FILES rejected by require")
}
