package com.kuriousdragon.smartindex.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.feature.home.R
import com.kuriousdragon.smartindex.feature.home.format.A11yLabels
import com.kuriousdragon.smartindex.feature.home.model.Tab
import com.kuriousdragon.smartindex.feature.home.state.EmptyKind
import com.kuriousdragon.smartindex.feature.home.state.ErrorKind

private val StateCircle = 72.dp
private val StateIcon = 36.dp
private val StateMaxWidth = 640.dp

/** A labelled action of a state view; the first one is filled, the second a text button. */
private class StateAction(val label: String, val onClick: () -> Unit)

/**
 * Shared layout of every centred state: a 72 dp circle with a 36 dp icon, title, body, a filled button and an
 * optional text button. Scrolls when it does not fit (200 % font). Pass `Modifier.fillParentMaxSize()` from a
 * `LazyColumn` item to centre it in the list area.
 */
@Composable
private fun StateLayout(
    tag: String,
    icon: ImageVector,
    circleColor: Color,
    iconTint: Color,
    title: String,
    body: String?,
    actions: List<StateAction>,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    Column(
        modifier = modifier
            .testTag(tag)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier.size(StateCircle).background(circleColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(StateIcon))
        }
        Column(
            modifier = Modifier.widthIn(max = StateMaxWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (body != null) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        actions.forEachIndexed { index, action ->
            if (index == 0) {
                Button(onClick = action.onClick) { Text(action.label) }
            } else {
                TextButton(onClick = action.onClick) { Text(action.label) }
            }
        }
    }
}

@Composable
private fun neutralCircle() = MaterialTheme.colorScheme.surfaceContainerHigh

@Composable
private fun neutralIcon() = MaterialTheme.colorScheme.onSurfaceVariant

/** Blank search with no date filter: the search icon, `search_empty_*` and the examples line. Tag `sidx_hint`. */
@Composable
fun HintView(modifier: Modifier = Modifier) {
    StateLayout(
        tag = HomeTestTags.HINT,
        icon = Icons.Filled.Search,
        circleColor = neutralCircle(),
        iconTint = neutralIcon(),
        title = stringResource(R.string.search_empty_title),
        body = stringResource(R.string.search_empty_body),
        note = stringResource(R.string.search_empty_examples),
        actions = emptyList(),
        modifier = modifier,
    )
}

/**
 * The four empty states (LLD §4.6), tag per [kind]:
 *  - EMPTY_DAY: [dayLabel] fills the body; action *Pick another date* ([onPickDate]).
 *  - EMPTY_TAB: [tab]'s label fills the title, [dayLabel] the body; action *Show all types* ([onShowAllTypes]).
 *  - NO_RESULTS: [query] fills the title; [dateFilterLabel] (null = no filter) adds the date note and *Clear date
 *    filter* ([onClearDateFilter]); *Search all types* ([onSearchAllTypes]) only when [tab] is not ALL.
 *  - EMPTY_RANGE: [dayLabel] is the range; action *Clear date filter* ([onClearDateFilter]).
 */
@Composable
fun EmptyView(
    kind: EmptyKind,
    modifier: Modifier = Modifier,
    dayLabel: String = "",
    tab: Tab = Tab.ALL,
    query: String = "",
    dateFilterLabel: String? = null,
    onPickDate: () -> Unit = {},
    onShowAllTypes: () -> Unit = {},
    onClearDateFilter: () -> Unit = {},
    onSearchAllTypes: () -> Unit = {},
) {
    val res = LocalResources.current
    val circle = neutralCircle()
    val tint = neutralIcon()
    when (kind) {
        EmptyKind.EMPTY_DAY -> StateLayout(
            tag = HomeTestTags.EMPTY_DAY,
            icon = Icons.Filled.CalendarMonth,
            circleColor = circle,
            iconTint = tint,
            title = stringResource(R.string.empty_day_title),
            body = stringResource(R.string.empty_day_body, dayLabel),
            actions = listOf(StateAction(stringResource(R.string.empty_day_action), onPickDate)),
            modifier = modifier,
        )

        EmptyKind.EMPTY_TAB -> StateLayout(
            tag = HomeTestTags.EMPTY_TAB,
            icon = Icons.Filled.FilterList,
            circleColor = circle,
            iconTint = tint,
            title = stringResource(
                R.string.empty_tab_title,
                A11yLabels.tabLabel(tab, res).lowercase(res.configuration.locales[0]),
            ),
            body = stringResource(R.string.empty_tab_body, dayLabel),
            actions = listOf(StateAction(stringResource(R.string.empty_tab_action), onShowAllTypes)),
            modifier = modifier,
        )

        EmptyKind.NO_RESULTS -> StateLayout(
            tag = HomeTestTags.NO_RESULTS,
            icon = Icons.Filled.SearchOff,
            circleColor = circle,
            iconTint = tint,
            title = stringResource(R.string.no_results_title, query),
            body = stringResource(R.string.no_results_body),
            note = dateFilterLabel?.let { stringResource(R.string.no_results_date_note, it) },
            actions = buildList {
                if (dateFilterLabel != null) {
                    add(StateAction(stringResource(R.string.no_results_action_clear_date), onClearDateFilter))
                }
                if (tab != Tab.ALL) {
                    add(StateAction(stringResource(R.string.no_results_action_all_types), onSearchAllTypes))
                }
            },
            modifier = modifier,
        )

        EmptyKind.EMPTY_RANGE -> StateLayout(
            tag = HomeTestTags.EMPTY_RANGE,
            icon = Icons.Filled.CalendarMonth,
            circleColor = circle,
            iconTint = tint,
            title = stringResource(R.string.empty_range_title),
            body = stringResource(R.string.empty_range_body, dayLabel),
            actions = listOf(StateAction(stringResource(R.string.no_results_action_clear_date), onClearDateFilter)),
            modifier = modifier,
        )
    }
}

/** Copy parts of a locked tab (LLD §4.9): noun, permission and thing. */
private class TabPermissionCopy(val noun: Int, val permission: Int, val thing: Int)

private fun tabPermissionCopy(tab: Tab): TabPermissionCopy = when (tab) {
    Tab.PHOTOS, Tab.VIDEOS -> TabPermissionCopy(
        R.string.tab_perm_noun_photos_videos,
        R.string.tab_perm_permission_photos_videos,
        R.string.tab_perm_thing_photos_videos,
    )

    Tab.DOCUMENTS -> TabPermissionCopy(
        R.string.tab_perm_noun_files,
        R.string.tab_perm_permission_all_files,
        R.string.tab_perm_thing_documents,
    )

    Tab.OTHER_FILES -> TabPermissionCopy(
        R.string.tab_perm_noun_files,
        R.string.tab_perm_permission_all_files,
        R.string.tab_perm_thing_files,
    )

    Tab.RECORDINGS -> TabPermissionCopy(
        R.string.tab_perm_noun_recordings,
        R.string.tab_perm_permission_all_files,
        R.string.tab_perm_thing_recordings,
    )

    Tab.CALLS -> TabPermissionCopy(
        R.string.tab_perm_noun_calls,
        R.string.tab_perm_permission_calls,
        R.string.tab_perm_thing_calls,
    )

    Tab.MESSAGES -> TabPermissionCopy(
        R.string.tab_perm_noun_messages,
        R.string.tab_perm_permission_messages,
        R.string.tab_perm_thing_messages,
    )

    Tab.ALL -> error("The ALL tab is never locked")
}

/**
 * A permission-locked tab: lock icon, title and body per tab (LLD §4.9). DENIED_PERMANENTLY shows only *Open app
 * settings* ([onOpenSettings]); every other state shows *Allow access* ([onAllow]). Tag `sidx_tab_locked`.
 */
@Suppress("UNUSED_PARAMETER") // [group] is part of the contract; the copy is keyed by [tab].
@Composable
fun TabLockedView(
    tab: Tab,
    group: Group,
    state: GroupState,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val res = LocalResources.current
    val copy = tabPermissionCopy(tab)
    val thing = res.getString(copy.thing)
    val action = if (state == GroupState.DENIED_PERMANENTLY) {
        StateAction(stringResource(R.string.action_open_app_settings), onOpenSettings)
    } else {
        StateAction(stringResource(R.string.action_allow_access), onAllow)
    }
    StateLayout(
        tag = HomeTestTags.TAB_LOCKED,
        icon = Icons.Filled.Lock,
        circleColor = neutralCircle(),
        iconTint = neutralIcon(),
        title = res.getString(R.string.tab_perm_title, res.getString(copy.noun)),
        body = res.getString(
            R.string.tab_perm_body,
            res.getString(copy.permission),
            thing,
            thing.replaceFirstChar { it.titlecase(res.configuration.locales[0]) },
        ),
        actions = listOf(action),
        modifier = modifier,
    )
}

/** Load failure of the list: a warning in an `errorContainer` circle and *Try again*. Tag `sidx_load_error`. */
@Composable
fun ErrorView(kind: ErrorKind, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    StateLayout(
        tag = HomeTestTags.LOAD_ERROR,
        icon = Icons.Filled.Warning,
        circleColor = MaterialTheme.colorScheme.errorContainer,
        iconTint = MaterialTheme.colorScheme.onErrorContainer,
        title = stringResource(
            if (kind == ErrorKind.DAY) R.string.load_error_title_day else R.string.load_error_title_search,
        ),
        body = stringResource(R.string.load_error_body),
        actions = listOf(StateAction(stringResource(R.string.action_retry), onRetry)),
        modifier = modifier,
    )
}

/** Footer item shown when appending a page failed: *Try again* → `retry()`. */
@Composable
fun AppendErrorFooter(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}
