package com.kuriousdragon.smartindex.feature.home.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kuriousdragon.smartindex.core.designsystem.theme.Spacing
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.feature.home.format.A11yLabels
import com.kuriousdragon.smartindex.feature.home.model.DeviceEnv
import com.kuriousdragon.smartindex.feature.home.model.Tab as HomeTab
import com.kuriousdragon.smartindex.feature.home.state.TabAccess
import java.text.NumberFormat

private val BadgeHeight = 16.dp
private val LockSize = 14.dp
private val ProgressHeight = 4.dp

/**
 * The 8 type tabs with count badges and 🔒 for permission-locked types (LLD §4.1, Design Spec §6.3). A badge is
 * hidden when [counts] is null. The selected tab is always scrolled into view. A thin indeterminate progress line sits
 * directly under the row while [showProgress].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TypeTabs(
    selected: HomeTab,
    counts: Map<ItemType, Int>?,
    tabAccess: Map<HomeTab, TabAccess>,
    env: DeviceEnv,
    showProgress: Boolean,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val res = LocalResources.current
    val tabs = HomeTab.entries
    val requesters = remember { tabs.associateWith { BringIntoViewRequester() } }
    LaunchedEffect(selected) { requesters.getValue(selected).bringIntoView() }
    val format = remember(env.locale) { NumberFormat.getIntegerInstance(env.locale) }
    Column(modifier = modifier.fillMaxWidth()) {
        PrimaryScrollableTabRow(
            selectedTabIndex = tabs.indexOf(selected),
            edgePadding = Spacing.S,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            tabs.forEach { tab ->
                val locked = tabAccess[tab] is TabAccess.Locked
                val count = tabCount(tab, counts)
                val description = A11yLabels.tabDescription(tab, count.takeUnless { locked }, locked, env, res)
                val isSelected = tab == selected
                Tab(
                    selected = isSelected,
                    onClick = { onSelect(tab) },
                    modifier = Modifier
                        .heightIn(min = Spacing.MinTouch)
                        .bringIntoViewRequester(requesters.getValue(tab))
                        .testTag(tab.tag)
                        .semantics { contentDescription = description },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                        ) {
                            Text(
                                text = A11yLabels.tabLabel(tab, res),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                            )
                            when {
                                locked -> Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(LockSize),
                                )

                                count != null -> CountBadge(format.format(count), isSelected)
                            }
                        }
                    },
                )
            }
        }
        if (showProgress) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(ProgressHeight))
        }
    }
}

/** Badge number for [tab]: ALL is the sum of every type; null while the counts are unknown. */
private fun tabCount(tab: HomeTab, counts: Map<ItemType, Int>?): Int? = when {
    counts == null -> null
    tab.type == null -> counts.values.sum()
    else -> counts[tab.type]
}

@Composable
private fun CountBadge(text: String, selected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .heightIn(min = BadgeHeight)
            .widthIn(min = BadgeHeight)
            .background(
                color = if (selected) scheme.primary else scheme.surfaceContainerHigh,
                shape = RoundedCornerShape(BadgeHeight / 2),
            )
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
