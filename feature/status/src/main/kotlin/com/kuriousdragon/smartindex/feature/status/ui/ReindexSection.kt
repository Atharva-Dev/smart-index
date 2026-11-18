package com.kuriousdragon.smartindex.feature.status.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.kuriousdragon.smartindex.core.designsystem.theme.Spacing
import com.kuriousdragon.smartindex.feature.status.R
import com.kuriousdragon.smartindex.feature.status.model.ReindexUi

/** Full-width "Re-index everything" button; while disabled the helper text sits under it and is its state description. */
@Composable
internal fun ReindexSection(state: ReindexUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val helper = stringResource(R.string.status_reindex_helper)
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.L, vertical = Spacing.S)) {
        OutlinedButton(
            onClick = onClick,
            enabled = state.enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.MinTouch)
                .testTag(StatusTestTags.REINDEX)
                .semantics { if (!state.enabled) stateDescription = helper },
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.status_reindex), modifier = Modifier.padding(start = Spacing.S))
        }
        if (!state.enabled) {
            Text(
                helper,
                modifier = Modifier.padding(top = Spacing.XS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
