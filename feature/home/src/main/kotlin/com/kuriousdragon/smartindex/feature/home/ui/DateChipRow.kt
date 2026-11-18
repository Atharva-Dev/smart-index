package com.kuriousdragon.smartindex.feature.home.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kuriousdragon.smartindex.core.designsystem.theme.Spacing
import com.kuriousdragon.smartindex.feature.home.R
import com.kuriousdragon.smartindex.feature.home.format.DateLabels
import com.kuriousdragon.smartindex.feature.home.model.DateFilter
import com.kuriousdragon.smartindex.feature.home.model.DeviceEnv

private val ChipHeight = 32.dp

/**
 * The single Search date chip (Design Spec §6.2): idle outlined "Any date ▾"; applied shows the range with a ✕ that
 * clears it. Tapping the label opens the date sheet. The row is padded to a 48 dp touch height.
 */
@Composable
fun DateChipRow(
    filter: DateFilter,
    env: DeviceEnv,
    onOpen: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val applied = filter != DateFilter.AnyDate
    val label = DateLabels.chip(filter, env, LocalResources.current)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.MinTouch)
            .padding(horizontal = Spacing.ListSide),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.testTag(HomeTestTags.DATE_CHIP),
            shape = CircleShape,
            color = if (applied) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = if (applied) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            border = if (applied) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(
                modifier = Modifier.heightIn(min = ChipHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = ChipHeight)
                        .clickable(role = Role.Button, onClick = onOpen)
                        .padding(start = Spacing.M, end = if (applied) Spacing.S else Spacing.XS),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = Spacing.S),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!applied) Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                if (applied) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.no_results_action_clear_date),
                        modifier = Modifier
                            .padding(end = Spacing.XS)
                            .size(ChipHeight)
                            .clickable(role = Role.Button, onClick = onClear)
                            .padding(7.dp),
                    )
                }
            }
        }
    }
}
