package com.kuriousdragon.smartindex.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kuriousdragon.smartindex.core.designsystem.theme.SmartIndexTheme
import com.kuriousdragon.smartindex.core.designsystem.theme.Spacing
import com.kuriousdragon.smartindex.core.designsystem.theme.TypeColorKey
import com.kuriousdragon.smartindex.feature.onboarding.R
import com.kuriousdragon.smartindex.feature.onboarding.model.SummaryRow
import com.kuriousdragon.smartindex.feature.onboarding.model.SummaryStatus
import com.kuriousdragon.smartindex.feature.onboarding.model.groupName

private val RowMinHeight = 56.dp
private val RowCircle = 40.dp
private val RowIcon = 20.dp
private val StatusCheck = 20.dp

/** The Summary page: one row per permission group and a pinned **Start indexing** button (disabled while starting). */
@Composable
internal fun SummaryPage(
    rows: List<SummaryRow>,
    isStarting: Boolean,
    onStartIndexing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PageScaffold(
        modifier = modifier.testTag(OnboardingTestTags.SUMMARY),
        actions = {
            PinnedActions(
                primaryText = stringResource(R.string.summary_cta),
                primaryTag = OnboardingTestTags.START,
                onPrimary = onStartIndexing,
                primaryEnabled = !isStarting,
            )
        },
    ) {
        Spacer(Modifier.height(Spacing.XXL))
        Text(
            text = stringResource(R.string.summary_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.M))
        Text(
            text = stringResource(R.string.summary_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.XL))
        rows.forEach { row -> SummaryRowItem(row) }
        Spacer(Modifier.height(Spacing.L))
        Text(
            text = stringResource(R.string.summary_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(Spacing.XL))
    }
}

@Composable
private fun SummaryRowItem(row: SummaryRow) {
    val colors = groupColors(row.group)
    val name = stringResource(groupName(row.group))
    val statusText =
        stringResource(
            when (row.status) {
                SummaryStatus.ALLOWED -> R.string.summary_status_allowed
                SummaryStatus.ALLOWED_SELECTED_ONLY -> R.string.summary_status_partial
                SummaryStatus.SKIPPED -> R.string.summary_status_skipped
            },
        )
    val description = "$name, ${statusText.replaceFirstChar { it.lowercase() }}"
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = RowMinHeight)
                .testTag(OnboardingTestTags.summaryRow(row.group))
                .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        Box(
            modifier = Modifier.size(RowCircle).clip(CircleShape).background(colors.tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = groupIcon(row.group),
                contentDescription = null,
                tint = colors.icon,
                modifier = Modifier.size(RowIcon),
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        StatusLabel(row.status, statusText)
    }
}

@Composable
private fun StatusLabel(status: SummaryStatus, text: String) {
    if (status == SummaryStatus.SKIPPED) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        val tint: Color = SmartIndexTheme.colors.types.getValue(TypeColorKey.CALL).icon
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(StatusCheck),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
        }
    }
}
