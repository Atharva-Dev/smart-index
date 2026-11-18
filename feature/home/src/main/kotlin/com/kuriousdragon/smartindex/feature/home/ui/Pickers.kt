package com.kuriousdragon.smartindex.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.kuriousdragon.smartindex.core.designsystem.theme.Radius
import com.kuriousdragon.smartindex.core.designsystem.theme.Spacing
import com.kuriousdragon.smartindex.feature.home.R
import com.kuriousdragon.smartindex.feature.home.format.DateLabels
import com.kuriousdragon.smartindex.feature.home.model.DateFilter
import com.kuriousdragon.smartindex.feature.home.model.DeviceEnv
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private val DialogMaxWidth = 640.dp
private const val FULL_SCREEN_BELOW_DP = 600

/** The M3 pickers speak UTC-midnight milliseconds, never the device zone. */
internal fun Long.utcMillisToLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

internal fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** Days after [today] (and years after its year) cannot be selected; there is no lower bound. */
@OptIn(ExperimentalMaterial3Api::class)
internal fun selectableUpTo(today: LocalDate): SelectableDates = object : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = !utcTimeMillis.utcMillisToLocalDate().isAfter(today)

    override fun isSelectableYear(year: Int): Boolean = year <= today.year
}

/**
 * Single-day picker (Design Spec 6.2, 6.10): headline [title] (`date_picker_title`), optional **Today** shortcut
 * bottom-left, **Cancel** and **OK** bottom-right, 28 dp radius. [onConfirm] is not called for a cleared selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPickerDialog(
    initial: LocalDate,
    today: LocalDate,
    title: String,
    showTodayShortcut: Boolean,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toUtcMillis(),
        initialDisplayedMonthMillis = initial.toUtcMillis(),
        selectableDates = selectableUpTo(today),
    )
    val selected = state.selectedDateMillis?.utcMillisToLocalDate()
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.widthIn(max = DialogMaxWidth).padding(horizontal = Spacing.L),
            shape = RoundedCornerShape(Radius.Sheet),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column {
                DatePicker(
                    state = state,
                    title = null,
                    headline = {
                        Text(
                            text = title,
                            modifier = Modifier.padding(start = Spacing.XL, end = Spacing.XL, bottom = Spacing.S),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    },
                    showModeToggle = false,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = Spacing.S, end = Spacing.L, bottom = Spacing.S),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (showTodayShortcut) {
                        TextButton(
                            onClick = {
                                state.selectedDateMillis = today.toUtcMillis()
                                state.displayedMonthMillis = today.toUtcMillis()
                            },
                        ) { Text(stringResource(R.string.picker_today)) }
                        Spacer(Modifier.weight(1f))
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(onClick = { selected?.let(onConfirm) }, enabled = selected != null) {
                        Text(stringResource(R.string.action_ok))
                    }
                }
            }
        }
    }
}

/**
 * Custom range picker (Design Spec 6.10). The headline is [R.string.range_picker_title] until a start day is chosen,
 * then the live label ("1 – 3 Nov 2026"). **Clear** calls [onClear]; **OK** returns `(start, end)` with `end == null`
 * when only one day was picked. Full-screen when the window is shorter than 600 dp, a 28 dp card (max 640 dp) otherwise.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangePickerDialog(
    initial: DateFilter.Range?,
    today: LocalDate,
    onConfirm: (LocalDate, LocalDate?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial?.start?.toUtcMillis(),
        initialSelectedEndDateMillis = initial?.end?.toUtcMillis(),
        initialDisplayedMonthMillis = (initial?.start ?: today).toUtcMillis(),
        selectableDates = selectableUpTo(today),
    )
    val start = state.selectedStartDateMillis?.utcMillisToLocalDate()
    val end = state.selectedEndDateMillis?.utcMillisToLocalDate()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val fullScreen = with(density) { LocalWindowInfo.current.containerSize.height.toDp() } < FULL_SCREEN_BELOW_DP.dp
    val locale = configuration.locales[0]
    val headline = if (start == null) {
        stringResource(R.string.range_picker_title)
    } else {
        val env = DeviceEnv(ZoneId.systemDefault(), locale, is24Hour = true, today = today)
        val filter = if (end == null || end == start) DateFilter.Day(start) else DateFilter.Range(start, end)
        DateLabels.chip(filter, env, LocalResources.current)
    }
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = !fullScreen),
    ) {
        Surface(
            modifier = if (fullScreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier.widthIn(max = DialogMaxWidth).padding(horizontal = Spacing.L)
            },
            shape = if (fullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(Radius.Sheet),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column {
                DateRangePicker(
                    state = state,
                    modifier = Modifier.weight(1f, fill = fullScreen),
                    title = null,
                    headline = {
                        Text(
                            text = headline,
                            modifier = Modifier.padding(start = Spacing.XL, end = Spacing.XL, bottom = Spacing.S),
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    showModeToggle = false,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = Spacing.S, end = Spacing.L, bottom = Spacing.S),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(
                        onClick = { start?.let { onConfirm(it, end?.takeIf { e -> e != start }) } },
                        enabled = start != null,
                    ) { Text(stringResource(R.string.action_ok)) }
                }
            }
        }
    }
}
