package com.kuriousdragon.smartindex.feature.home.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kuriousdragon.smartindex.core.designsystem.theme.Spacing
import com.kuriousdragon.smartindex.feature.home.R

/** The ⋮ menu of the search bar: "Index status" and "Permissions" (Design Spec §6.1). Items are 48 dp tall. */
@Composable
fun OverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onStatus: () -> Unit,
    onPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = modifier) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_index_status)) },
            onClick = {
                onDismiss()
                onStatus()
            },
            modifier = Modifier.heightIn(min = Spacing.MinTouch),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_permissions)) },
            onClick = {
                onDismiss()
                onPermissions()
            },
            modifier = Modifier.heightIn(min = Spacing.MinTouch),
        )
    }
}
