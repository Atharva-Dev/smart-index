package com.kuriousdragon.smartindex.feature.status.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kuriousdragon.smartindex.core.designsystem.theme.SmartIndexTheme
import com.kuriousdragon.smartindex.core.designsystem.theme.Spacing
import com.kuriousdragon.smartindex.core.designsystem.theme.typeColorKey
import com.kuriousdragon.smartindex.feature.status.R
import com.kuriousdragon.smartindex.feature.status.format.StatusFormatter
import com.kuriousdragon.smartindex.feature.status.model.SubLine
import com.kuriousdragon.smartindex.feature.status.model.TypeRowUi

/** One "Indexed items" row (LLD §3.5): coloured tile, name, optional sub-line and the count. One merged node. */
@Composable
internal fun TypeRow(row: TypeRowUi, formatter: StatusFormatter, modifier: Modifier = Modifier) {
    val colors = SmartIndexTheme.colors.types.getValue(typeColorKey(row.type, null))
    val name = stringResource(typeNameRes(row.type))
    val subText = when (val sub = row.subLine) {
        SubLine.None -> null
        SubLine.DocumentKinds -> stringResource(R.string.status_doc_sub)
        is SubLine.RecordingsLinked -> stringResource(R.string.status_rec_sub, formatter.count(sub.n))
        SubLine.NoAccess -> stringResource(R.string.status_noaccess)
    }
    val count = row.count
    val countText = if (count == null) stringResource(R.string.status_count_none) else formatter.count(count)
    val description = when {
        row.subLine == SubLine.NoAccess -> stringResource(R.string.a11y_type_no_access, name)
        count == null -> stringResource(R.string.a11y_type_counting, name)
        subText == null -> stringResource(R.string.a11y_type_with_count, name, countText)
        else -> stringResource(R.string.a11y_type_with_sub, name, countText, subText)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(StatusTestTags.typeRow(row.type))
            .semantics(mergeDescendants = true) { contentDescription = description }
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.L, vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.L),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(typeIcon(row.type), contentDescription = null, tint = colors.icon, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subText != null) {
                Text(
                    subText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.subLine == SubLine.NoAccess) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        Text(
            countText,
            modifier = Modifier.testTag(StatusTestTags.typeCount(row.type)),
            style = MaterialTheme.typography.labelLarge,
            color = if (count ==
                null
            ) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
