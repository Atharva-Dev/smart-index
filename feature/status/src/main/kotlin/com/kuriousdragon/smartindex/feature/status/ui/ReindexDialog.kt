package com.kuriousdragon.smartindex.feature.status.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kuriousdragon.smartindex.core.designsystem.theme.Spacing
import com.kuriousdragon.smartindex.feature.status.R

/** Confirmation before a full re-index. */
@Composable
internal fun ReindexDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(24.dp)) },
        title = { Text(stringResource(R.string.reindex_title)) },
        text = { Text(stringResource(R.string.reindex_body)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = Spacing.MinTouch).testTag(StatusTestTags.REINDEX_CONFIRM),
            ) { Text(stringResource(R.string.reindex_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Spacing.MinTouch)) {
                Text(stringResource(R.string.reindex_cancel))
            }
        },
    )
}
