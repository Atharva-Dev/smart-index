package com.kuriousdragon.smartindex.feature.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kuriousdragon.smartindex.core.designsystem.theme.Spacing
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.ResultItem
import com.kuriousdragon.smartindex.feature.home.R
import com.kuriousdragon.smartindex.feature.home.format.DurationFormat
import com.kuriousdragon.smartindex.feature.home.format.nonBlank
import com.kuriousdragon.smartindex.feature.home.model.DateFilter

private val SheetMaxWidth = 640.dp
private val RowHeight = 56.dp
private val RowHeightWithSub = 64.dp

/** The modal bottom sheet frame: drag handle, 640 dp max width, scrollable so 200 % font never clips the items. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetFrame(
    tag: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = SheetMaxWidth,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        ) {
            content()
        }
    }
}

/** Header with the leading visual, a one-line title, the sub-line and a divider underneath. */
@Composable
private fun SheetHeader(item: ResultItem, title: String, sub: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.SheetSide, vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.L),
    ) {
        TypeTile(type = item.type, direction = item.direction, extLabel = fileExt(item))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = if (item.type in FILE_TYPES) TextOverflow.MiddleEllipsis else TextOverflow.Ellipsis,
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

private val FILE_TYPES = setOf(ItemType.PHOTO, ItemType.VIDEO, ItemType.DOCUMENT, ItemType.OTHER_FILE)

private fun fileExt(item: ResultItem): String? = when (item.type) {
    ItemType.DOCUMENT, ItemType.OTHER_FILE, ItemType.RECORDING ->
        item.ext.nonBlank()?.trimStart('.') ?: item.title.substringAfterLast('.', "").takeIf { it.isNotBlank() }

    else -> null
}

/** One 56 dp row (64 dp with a sub-line) of a sheet. */
@Composable
private fun SheetRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    selected: Boolean? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (sub != null) RowHeightWithSub else RowHeight)
            .then(
                if (selected != null) Modifier.semantics { this.selected = selected } else Modifier,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.SheetSide, vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (sub != null) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected == true) {
            Spacer(Modifier.width(Spacing.L))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.sheet_selected),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * File actions (Design Spec 6.6): **Open** and **Open with...**. [header] is the sub-line
 * `<folder> . <size> . <EEE d MMM, time>` (see `DateLabels.fileSheetHeader`). Every action dismisses the sheet first.
 */
@Composable
fun FileSheet(
    item: ResultItem,
    header: String,
    onOpen: () -> Unit,
    onOpenWith: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SheetFrame(tag = HomeTestTags.SHEET_FILE, onDismiss = onDismiss, modifier = modifier) {
        SheetHeader(item, title = item.title, sub = header)
        SheetRow(
            label = stringResource(R.string.sheet_open),
            sub = stringResource(R.string.sheet_open_sub),
            onClick = { dismissThen(onDismiss, onOpen) },
        )
        SheetRow(
            label = stringResource(R.string.sheet_open_with),
            sub = stringResource(R.string.sheet_open_with_sub),
            onClick = { dismissThen(onDismiss, onOpenWith) },
        )
    }
}

/**
 * Call actions (Design Spec 6.6). [header] is `<direction> . <duration> . <date, time>`. Items: Call back (only with a
 * [hasNumber], the number as sub-line), Open contact ([known]) or Add to contacts, Play recording ([hasRecording],
 * the recording length as sub-line when known).
 */
@Composable
@Suppress("LongParameterList")
fun CallSheet(
    item: ResultItem,
    header: String,
    known: Boolean,
    hasNumber: Boolean,
    hasRecording: Boolean,
    onCallBack: () -> Unit,
    onOpenContact: () -> Unit,
    onAddContact: () -> Unit,
    onPlayRecording: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SheetFrame(tag = HomeTestTags.SHEET_CALL, onDismiss = onDismiss, modifier = modifier) {
        val title = item.contactName.nonBlank() ?: item.number.nonBlank() ?: item.title
        SheetHeader(item, title = title, sub = header)
        if (hasNumber) {
            SheetRow(
                label = stringResource(R.string.sheet_call_back),
                sub = item.number,
                onClick = { dismissThen(onDismiss, onCallBack) },
            )
        }
        if (known) {
            SheetRow(
                label = stringResource(R.string.sheet_open_contact),
                onClick = { dismissThen(onDismiss, onOpenContact) },
            )
        } else {
            SheetRow(
                label = stringResource(R.string.sheet_add_contact),
                sub = stringResource(R.string.sheet_add_contact_sub),
                onClick = { dismissThen(onDismiss, onAddContact) },
            )
        }
        if (hasRecording) {
            SheetRow(
                label = stringResource(R.string.sheet_play_recording),
                sub = item.durationMs?.let(DurationFormat::media),
                onClick = { dismissThen(onDismiss, onPlayRecording) },
            )
        }
    }
}

/** Recording actions: **Play** (**Pause** when [isCurrentAndPlaying]) and **Open with...** (audio chooser). */
@Composable
fun RecordingSheet(
    item: ResultItem,
    header: String,
    isCurrentAndPlaying: Boolean,
    onPlayPause: () -> Unit,
    onOpenWith: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SheetFrame(tag = HomeTestTags.SHEET_RECORDING, onDismiss = onDismiss, modifier = modifier) {
        val title = item.contactName.nonBlank() ?: item.number.nonBlank() ?: item.title
        SheetHeader(item, title = title, sub = header)
        SheetRow(
            label = stringResource(if (isCurrentAndPlaying) R.string.sheet_pause else R.string.sheet_play),
            onClick = { dismissThen(onDismiss, onPlayPause) },
        )
        SheetRow(
            label = stringResource(R.string.sheet_open_with),
            onClick = { dismissThen(onDismiss, onOpenWith) },
        )
    }
}

/**
 * "Filter by date" (Design Spec 6.10). The presets call [onPreset] with the preset itself; **Pick a day...** and
 * **Custom range...** only ask the caller to open the matching picker. The check mark sits on the row of [current].
 */
@Composable
fun DateFilterSheet(
    current: DateFilter,
    onPreset: (DateFilter) -> Unit,
    onPickDay: () -> Unit,
    onCustomRange: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SheetFrame(tag = null, onDismiss = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.SheetSide, vertical = Spacing.S),
        ) {
            Text(
                text = stringResource(R.string.date_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.date_sheet_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        PresetRow(R.string.date_any, HomeTestTags.DATE_PRESET_ANY, current == DateFilter.AnyDate) {
            dismissThen(onDismiss) { onPreset(DateFilter.AnyDate) }
        }
        PresetRow(R.string.date_preset_today, HomeTestTags.DATE_PRESET_TODAY, current == DateFilter.Today) {
            dismissThen(onDismiss) { onPreset(DateFilter.Today) }
        }
        PresetRow(
            R.string.date_preset_yesterday,
            HomeTestTags.DATE_PRESET_YESTERDAY,
            current == DateFilter.Yesterday,
        ) { dismissThen(onDismiss) { onPreset(DateFilter.Yesterday) } }
        PresetRow(
            R.string.date_preset_last_7_days,
            HomeTestTags.DATE_PRESET_LAST_7_DAYS,
            current == DateFilter.Last7Days,
        ) { dismissThen(onDismiss) { onPreset(DateFilter.Last7Days) } }
        PresetRow(
            R.string.date_preset_last_30_days,
            HomeTestTags.DATE_PRESET_LAST_30_DAYS,
            current == DateFilter.Last30Days,
        ) { dismissThen(onDismiss) { onPreset(DateFilter.Last30Days) } }
        SheetRow(
            label = stringResource(R.string.date_preset_pick_day),
            selected = current is DateFilter.Day,
            onClick = { dismissThen(onDismiss, onPickDay) },
        )
        SheetRow(
            label = stringResource(R.string.date_preset_custom_range),
            selected = current is DateFilter.Range,
            onClick = { dismissThen(onDismiss, onCustomRange) },
        )
    }
}

@Composable
private fun PresetRow(label: Int, tag: String, selected: Boolean, onClick: () -> Unit) {
    SheetRow(label = stringResource(label), selected = selected, onClick = onClick, modifier = Modifier.testTag(tag))
}

private fun dismissThen(onDismiss: () -> Unit, action: () -> Unit) {
    onDismiss()
    action()
}
