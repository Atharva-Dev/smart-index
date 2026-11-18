package com.kuriousdragon.smartindex.feature.status.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kuriousdragon.smartindex.core.designsystem.theme.SmartIndexTheme
import com.kuriousdragon.smartindex.core.designsystem.theme.Spacing
import com.kuriousdragon.smartindex.core.designsystem.theme.TypeColorKey
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.feature.status.R
import com.kuriousdragon.smartindex.feature.status.model.PermissionAction
import com.kuriousdragon.smartindex.feature.status.model.PermissionRowUi

/** One "Permissions" row (LLD §3.5): group name, state text and a tonal Allow / Allow all button. */
@Composable
internal fun PermissionRow(row: PermissionRowUi, onAllow: () -> Unit, modifier: Modifier = Modifier) {
    val name = stringResource(groupNameRes(row.group))
    val allowed = row.state == GroupState.GRANTED || row.state == GroupState.PARTIAL
    val rowDescription =
        stringResource(if (allowed) R.string.a11y_group_allowed else R.string.a11y_group_not_allowed, name)
    val buttonDescription = stringResource(R.string.a11y_allow_group, name)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(StatusTestTags.permissionRow(row.group))
            .semantics(mergeDescendants = true) { contentDescription = rowDescription }
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.L, vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.L),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            val stateText = when {
                row.state == GroupState.PARTIAL -> stringResource(R.string.status_allowed_partial)
                row.action == PermissionAction.NONE -> "✓ " + stringResource(R.string.status_allowed)
                else -> null
            }
            if (stateText != null) {
                Text(
                    stateText,
                    style = MaterialTheme.typography.labelMedium,
                    color = SmartIndexTheme.colors.types.getValue(TypeColorKey.CALL).icon,
                )
            }
        }
        if (row.action != PermissionAction.NONE) {
            FilledTonalButton(
                onClick = onAllow,
                modifier = Modifier
                    .heightIn(min = Spacing.MinTouch)
                    .testTag(StatusTestTags.allow(row.group))
                    .semantics { contentDescription = buttonDescription },
            ) {
                Text(
                    stringResource(
                        if (row.action ==
                            PermissionAction.ALLOW_ALL
                        ) {
                            R.string.status_allow_all
                        } else {
                            R.string.status_allow
                        },
                    ),
                )
            }
        }
    }
}
