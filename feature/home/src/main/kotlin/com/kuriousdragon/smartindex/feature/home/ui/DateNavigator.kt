package com.kuriousdragon.smartindex.feature.home.ui

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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kuriousdragon.smartindex.core.designsystem.theme.Spacing
import com.kuriousdragon.smartindex.feature.home.R
import com.kuriousdragon.smartindex.feature.home.format.DateLabels
import com.kuriousdragon.smartindex.feature.home.model.DeviceEnv
import java.time.LocalDate

private val RowHeight = 56.dp
private val ButtonHeight = 40.dp

/** Home day navigation: ‹ date button › (Design Spec §6.2). › is disabled when [canGoNext] is false. */
@Composable
fun DateNavigator(
    date: LocalDate,
    env: DeviceEnv,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val res = LocalResources.current
    val label = DateLabels.homeButton(date, env, res)
    val description = stringResource(R.string.a11y_date_button, DateLabels.full(date, env))
    val unavailable = stringResource(R.string.a11y_unavailable)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .padding(horizontal = Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPrev,
            modifier = Modifier.size(Spacing.MinTouch).testTag(HomeTestTags.PREV_DAY),
        ) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.a11y_prev_day))
        }
        Surface(
            modifier = Modifier
                .weight(1f, fill = false)
                .testTag(HomeTestTags.DATE_BUTTON)
                .clickable(onClick = onDateClick)
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = description
                    onClick {
                        onDateClick()
                        true
                    }
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier.heightIn(min = ButtonHeight).padding(start = Spacing.M, end = Spacing.S),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.CalendarToday,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = label,
                    modifier = Modifier.weight(1f, fill = false).padding(horizontal = Spacing.S),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(
            onClick = onNext,
            enabled = canGoNext,
            modifier = Modifier
                .size(Spacing.MinTouch)
                .testTag(HomeTestTags.NEXT_DAY)
                .semantics { if (!canGoNext) stateDescription = unavailable },
        ) {
            Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.a11y_next_day))
        }
    }
}
