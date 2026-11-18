package com.kuriousdragon.smartindex.feature.home

import android.content.res.Resources
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.kuriousdragon.smartindex.core.designsystem.theme.LocalReducedMotion
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.IndexConfig
import com.kuriousdragon.smartindex.core.model.ResultItem
import com.kuriousdragon.smartindex.core.model.ThumbnailRequest
import com.kuriousdragon.smartindex.core.permissions.ui.AllowInSettingsDialog
import com.kuriousdragon.smartindex.feature.home.format.DateLabels
import com.kuriousdragon.smartindex.feature.home.format.PlayerLabels
import com.kuriousdragon.smartindex.feature.home.format.RowAction
import com.kuriousdragon.smartindex.feature.home.format.RowFormatter
import com.kuriousdragon.smartindex.feature.home.format.nonBlank
import com.kuriousdragon.smartindex.feature.home.model.DateFilter
import com.kuriousdragon.smartindex.feature.home.model.DeviceEnv
import com.kuriousdragon.smartindex.feature.home.model.ListEntry
import com.kuriousdragon.smartindex.feature.home.model.ListGroup
import com.kuriousdragon.smartindex.feature.home.model.ListRequest
import com.kuriousdragon.smartindex.feature.home.model.PartOfDay
import com.kuriousdragon.smartindex.feature.home.model.Tab
import com.kuriousdragon.smartindex.feature.home.state.EmptyKind
import com.kuriousdragon.smartindex.feature.home.state.HomeMode
import com.kuriousdragon.smartindex.feature.home.state.HomeUiState
import com.kuriousdragon.smartindex.feature.home.state.ListStatus
import com.kuriousdragon.smartindex.feature.home.state.ListStatusInput
import com.kuriousdragon.smartindex.feature.home.state.Overlay
import com.kuriousdragon.smartindex.feature.home.state.RowPlayback
import com.kuriousdragon.smartindex.feature.home.state.TabAccess
import com.kuriousdragon.smartindex.feature.home.state.resolveListStatus
import com.kuriousdragon.smartindex.feature.home.ui.AppendErrorFooter
import com.kuriousdragon.smartindex.feature.home.ui.CallSheet
import com.kuriousdragon.smartindex.feature.home.ui.DateChipRow
import com.kuriousdragon.smartindex.feature.home.ui.DateFilterSheet
import com.kuriousdragon.smartindex.feature.home.ui.DateNavigator
import com.kuriousdragon.smartindex.feature.home.ui.DayPickerDialog
import com.kuriousdragon.smartindex.feature.home.ui.EmptyView
import com.kuriousdragon.smartindex.feature.home.ui.ErrorView
import com.kuriousdragon.smartindex.feature.home.ui.FileSheet
import com.kuriousdragon.smartindex.feature.home.ui.HintView
import com.kuriousdragon.smartindex.feature.home.ui.HomeTestTags
import com.kuriousdragon.smartindex.feature.home.ui.IndexingBanner
import com.kuriousdragon.smartindex.feature.home.ui.ListRow
import com.kuriousdragon.smartindex.feature.home.ui.PermissionBanner
import com.kuriousdragon.smartindex.feature.home.ui.RangePickerDialog
import com.kuriousdragon.smartindex.feature.home.ui.RecordingSheet
import com.kuriousdragon.smartindex.feature.home.ui.SearchBarIdle
import com.kuriousdragon.smartindex.feature.home.ui.SearchField
import com.kuriousdragon.smartindex.feature.home.ui.SectionHeader
import com.kuriousdragon.smartindex.feature.home.ui.SkeletonRow
import com.kuriousdragon.smartindex.feature.home.ui.TabLockedView
import com.kuriousdragon.smartindex.feature.home.ui.TypeTabs
import com.kuriousdragon.smartindex.feature.player.RecordingPlayer
import com.kuriousdragon.smartindex.feature.player.ui.MiniPlayerHost
import java.time.LocalDate
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val ContentMaxWidth = 640.dp
private val BannerPadding = 16.dp
private val ListBottomPadding = 16.dp
private const val FADE_IN_MS = 150

/** Every user intent of the screen. [forViewModel] binds them to a [HomeViewModel]. */
@Immutable
@Suppress("LongParameterList")
class HomeCallbacks(
    val onSearchBarClick: () -> Unit,
    val onOverflowStatus: () -> Unit,
    val onOverflowPermissions: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onImeSearch: () -> Unit,
    val onClearQuery: () -> Unit,
    val onBack: () -> Unit,
    val onPrevDay: () -> Unit,
    val onNextDay: () -> Unit,
    val onOpenHomeDatePicker: () -> Unit,
    val onOpenDateSheet: () -> Unit,
    val onClearDateFilter: () -> Unit,
    val onTabSelected: (Tab) -> Unit,
    val onShowAllTypes: () -> Unit,
    val onRowClick: (ResultItem) -> Unit,
    val onRowMoreClick: (ResultItem) -> Unit,
    val onPlayClick: (ResultItem) -> Unit,
    val onSheetOpen: (ResultItem, Boolean) -> Unit,
    val onSheetCallBack: (ResultItem) -> Unit,
    val onSheetOpenContact: (ResultItem) -> Unit,
    val onSheetAddContact: (ResultItem) -> Unit,
    val onSheetPlay: (ResultItem) -> Unit,
    val onBannerAllowAccess: () -> Unit,
    val onBannerNotNow: () -> Unit,
    val onTabAllowAccess: (Tab) -> Unit,
    val onTabOpenAppSettings: (Tab) -> Unit,
    val onSettingsDialogConfirm: (Group) -> Unit,
    val onDismissOverlay: () -> Unit,
    val onDateFilterSelected: (DateFilter) -> Unit,
    val onOpenSearchDayPicker: () -> Unit,
    val onOpenRangePicker: () -> Unit,
    val onDayPicked: (LocalDate) -> Unit,
    val onRangePicked: (LocalDate, LocalDate?) -> Unit,
) {
    companion object {
        fun forViewModel(vm: HomeViewModel) = HomeCallbacks(
            onSearchBarClick = vm::onSearchBarClick,
            onOverflowStatus = vm::onOverflowStatus,
            onOverflowPermissions = vm::onOverflowPermissions,
            onQueryChange = vm::onQueryChange,
            onImeSearch = vm::onImeSearch,
            onClearQuery = vm::onClearQuery,
            onBack = { vm.onBack() },
            onPrevDay = vm::onPrevDay,
            onNextDay = vm::onNextDay,
            onOpenHomeDatePicker = vm::onOpenHomeDatePicker,
            onOpenDateSheet = vm::onOpenDateSheet,
            onClearDateFilter = vm::onClearDateFilter,
            onTabSelected = vm::onTabSelected,
            onShowAllTypes = vm::onShowAllTypes,
            onRowClick = vm::onRowClick,
            onRowMoreClick = vm::onRowMoreClick,
            onPlayClick = vm::onPlayClick,
            onSheetOpen = vm::onSheetOpen,
            onSheetCallBack = vm::onSheetCallBack,
            onSheetOpenContact = vm::onSheetOpenContact,
            onSheetAddContact = vm::onSheetAddContact,
            onSheetPlay = vm::onSheetPlay,
            onBannerAllowAccess = vm::onBannerAllowAccess,
            onBannerNotNow = vm::onBannerNotNow,
            onTabAllowAccess = vm::onTabAllowAccess,
            onTabOpenAppSettings = vm::onTabOpenAppSettings,
            onSettingsDialogConfirm = vm::onSettingsDialogConfirm,
            onDismissOverlay = vm::onDismissOverlay,
            onDateFilterSelected = vm::onDateFilterSelected,
            onOpenSearchDayPicker = vm::onOpenSearchDayPicker,
            onOpenRangePicker = vm::onOpenRangePicker,
            onDayPicked = vm::onDayPicked,
            onRangePicked = vm::onRangePicked,
        )
    }
}

/**
 * The stateless Home / Search layout (LLD §3.2): search bar or field, date navigator or chip, type tabs, the one
 * list (banners first, then rows or one state view), mini-player at the bottom with the snackbar above it, and
 * the overlays of [HomeUiState.overlay].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    items: LazyPagingItems<ListEntry>,
    playback: RowPlayback,
    snackbarHost: SnackbarHostState,
    player: RecordingPlayer,
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
) {
    val refresh = items.loadState.refresh
    Scaffold(
        modifier = modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = { MiniPlayerHost(player) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxSize()) {
                TopArea(state, callbacks)
                TypeTabs(
                    selected = state.tab,
                    counts = state.counts,
                    tabAccess = state.tabAccess,
                    env = state.env,
                    showProgress = state.isCatchUp || (state.mode == HomeMode.SEARCH && refresh is LoadState.Loading),
                    onSelect = callbacks.onTabSelected,
                )
                HomeList(state, items, playback, callbacks, Modifier.weight(1f))
            }
        }
    }
    Overlays(state, playback, callbacks)
}

@Composable
private fun TopArea(state: HomeUiState, callbacks: HomeCallbacks) {
    if (state.mode == HomeMode.HOME) {
        SearchBarIdle(
            onClick = callbacks.onSearchBarClick,
            onOverflowStatus = callbacks.onOverflowStatus,
            onOverflowPermissions = callbacks.onOverflowPermissions,
        )
        DateNavigator(
            date = state.day,
            env = state.env,
            canGoNext = state.canGoNext,
            onPrev = callbacks.onPrevDay,
            onNext = callbacks.onNextDay,
            onDateClick = callbacks.onOpenHomeDatePicker,
        )
    } else {
        SearchField(
            query = state.query,
            onQueryChange = callbacks.onQueryChange,
            onSearch = callbacks.onImeSearch,
            onClear = callbacks.onClearQuery,
            onBack = callbacks.onBack,
        )
        DateChipRow(
            filter = state.dateFilter,
            env = state.env,
            onOpen = callbacks.onOpenDateSheet,
            onClear = callbacks.onClearDateFilter,
        )
    }
}

/**
 * Fades the list in once the refresh that follows a commit has finished. The alpha is always restored to 1 when
 * this is cancelled (a newer commit, or reduced motion switching on), so an interrupted fade can never leave the
 * list invisible (SIDX-102).
 */
internal suspend fun Animatable<Float, *>.fadeInAfterRefresh(refresh: Flow<LoadState>) {
    try {
        refresh
            .dropWhile { it !is LoadState.Loading }
            .first { it is LoadState.NotLoading }
        snapTo(0f)
        animateTo(1f, tween(FADE_IN_MS))
    } finally {
        withContext(NonCancellable) { snapTo(1f) }
    }
}

@Composable
@Suppress("LongMethod")
private fun HomeList(
    state: HomeUiState,
    items: LazyPagingItems<ListEntry>,
    playback: RowPlayback,
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val reducedMotion = LocalReducedMotion.current
    val listState = rememberLazyListState()
    val fade = remember { Animatable(1f) }
    val refresh = items.loadState.refresh
    val loading = refresh is LoadState.Loading

    val firstCommitId = remember { state.commit.id }
    LaunchedEffect(state.commit.id, reducedMotion) {
        if (reducedMotion) {
            fade.snapTo(1f)
            return@LaunchedEffect
        }
        if (state.commit.id == firstCommitId) return@LaunchedEffect
        listState.scrollToItem(0)
        fade.fadeInAfterRefresh(snapshotFlow { items.loadState.refresh })
    }

    var skeletonDue by remember(state.commit.id) { mutableStateOf(false) }
    LaunchedEffect(state.commit.id, loading) {
        if (loading) {
            delay(IndexConfig.SKELETON_DELAY_MS)
            skeletonDue = true
        } else {
            skeletonDue = false
        }
    }

    val request = state.commit.request
    val text = (request as? ListRequest.Search)?.query?.text.orEmpty()
    val status = resolveListStatus(
        ListStatusInput(
            request = request,
            refresh = refresh,
            itemCount = items.itemCount,
            skeletonDue = skeletonDue,
            counts = state.counts,
            mode = state.mode,
            text = text,
            dateFilter = state.dateFilter,
            tab = state.tab,
            groups = state.tabAccess.values.filterIsInstance<TabAccess.Locked>()
                .associate { it.group to it.state },
        ),
    )

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = ListBottomPadding),
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .testTag(HomeTestTags.LIST),
    ) {
        state.banners.indexing?.let {
            item(key = KEY_INDEXING_BANNER, contentType = TYPE_BANNER) {
                IndexingBanner(it, Modifier.padding(horizontal = BannerPadding, vertical = 4.dp))
            }
        }
        state.banners.permission?.let {
            item(key = KEY_PERMISSION_BANNER, contentType = TYPE_BANNER) {
                PermissionBanner(
                    ui = it,
                    onNotNow = callbacks.onBannerNotNow,
                    onAllow = callbacks.onBannerAllowAccess,
                    modifier = Modifier.padding(horizontal = BannerPadding, vertical = 4.dp),
                )
            }
        }
        when (status) {
            ListStatus.Content -> {
                items(
                    count = items.itemCount,
                    key = items.itemKey { it.key },
                    contentType = items.itemContentType { if (it is ListEntry.Row) TYPE_ROW else TYPE_HEADER },
                ) { index ->
                    when (val entry = items[index]) {
                        is ListEntry.Row -> RowEntry(entry.item, state.env, playback, callbacks)
                        is ListEntry.Header -> SectionHeader(headerText(entry.group, state.env, resources))
                        null -> Unit
                    }
                }
                if (items.loadState.append is LoadState.Error) {
                    item(key = KEY_APPEND_ERROR, contentType = TYPE_FOOTER) {
                        AppendErrorFooter(onRetry = { items.retry() })
                    }
                }
            }

            is ListStatus.Skeleton -> items(
                count = status.rows,
                key = { "skeleton$it" },
                contentType = { TYPE_SKELETON },
            ) { SkeletonRow() }

            else -> item(key = KEY_STATE, contentType = TYPE_STATE) {
                Box(modifier = Modifier.fillParentMaxSize()) {
                    StateContent(status, state, text, callbacks, onRetry = { items.refresh() })
                }
            }
        }
    }
}

@Composable
private fun RowEntry(item: ResultItem, env: DeviceEnv, playback: RowPlayback, callbacks: HomeCallbacks) {
    val resources = LocalResources.current
    val ui = remember(item, env) { RowFormatter.format(item, env, resources) }
    val current = ui.recordingId != null && ui.recordingId == playback.recordingId
    ListRow(
        ui = ui,
        playing = current && playback.isPlaying,
        isCurrent = current,
        onClick = { callbacks.onRowClick(item) },
        onMore = { callbacks.onRowMoreClick(item) },
        onPlayPause = { callbacks.onPlayClick(item) },
        onAction = { action -> performAction(action, item, callbacks) },
        thumbnail = remember(item) { ThumbnailRequest.of(item) },
    )
}

private fun performAction(action: RowAction, item: ResultItem, callbacks: HomeCallbacks) = when (action) {
    RowAction.OPEN_WITH, RowAction.OPEN_WITH_AUDIO -> callbacks.onSheetOpen(item, true)
    RowAction.MORE_ACTIONS -> callbacks.onRowMoreClick(item)
    RowAction.CALL_BACK -> callbacks.onSheetCallBack(item)
    RowAction.OPEN_CONTACT -> callbacks.onSheetOpenContact(item)
    RowAction.ADD_CONTACT -> callbacks.onSheetAddContact(item)
    RowAction.PLAY_RECORDING, RowAction.PLAY -> callbacks.onPlayClick(item)
}

@Composable
@Suppress("LongMethod")
private fun StateContent(
    status: ListStatus,
    state: HomeUiState,
    text: String,
    callbacks: HomeCallbacks,
    onRetry: () -> Unit,
) {
    val resources = LocalResources.current
    when (status) {
        ListStatus.Hint -> HintView()

        is ListStatus.TabLocked -> TabLockedView(
            tab = status.tab,
            group = status.group,
            state = status.state,
            onAllow = { callbacks.onTabAllowAccess(status.tab) },
            onOpenSettings = { callbacks.onTabOpenAppSettings(status.tab) },
        )

        is ListStatus.Error -> ErrorView(status.kind, onRetry)

        is ListStatus.Empty -> {
            val filterLabel = DateLabels.chip(state.dateFilter, state.env, resources)
            EmptyView(
                kind = status.kind,
                dayLabel = if (status.kind == EmptyKind.EMPTY_RANGE) {
                    filterLabel
                } else {
                    DateLabels.header(state.day, state.env, resources)
                },
                tab = state.tab,
                query = text,
                dateFilterLabel = filterLabel.takeIf { state.dateFilter != DateFilter.AnyDate },
                onPickDate = callbacks.onOpenHomeDatePicker,
                onShowAllTypes = callbacks.onShowAllTypes,
                onClearDateFilter = callbacks.onClearDateFilter,
                onSearchAllTypes = callbacks.onShowAllTypes,
            )
        }

        is ListStatus.Skeleton, ListStatus.Content -> Unit
    }
}

@Composable
@Suppress("LongMethod")
private fun Overlays(state: HomeUiState, playback: RowPlayback, callbacks: HomeCallbacks) {
    val env = state.env
    val resources = LocalResources.current
    when (val overlay = state.overlay) {
        Overlay.None -> Unit

        is Overlay.FileSheet -> FileSheet(
            item = overlay.item,
            header = DateLabels.fileSheetHeader(overlay.item, env),
            onOpen = { callbacks.onSheetOpen(overlay.item, false) },
            onOpenWith = { callbacks.onSheetOpen(overlay.item, true) },
            onDismiss = callbacks.onDismissOverlay,
        )

        is Overlay.CallSheet -> {
            val item = overlay.item
            CallSheet(
                item = item,
                header = callSheetHeader(item, env, resources),
                known = item.contactName.nonBlank() != null,
                hasNumber = item.number.nonBlank() != null,
                hasRecording = item.linkedRecordingId != null,
                onCallBack = { callbacks.onSheetCallBack(item) },
                onOpenContact = { callbacks.onSheetOpenContact(item) },
                onAddContact = { callbacks.onSheetAddContact(item) },
                onPlayRecording = { callbacks.onSheetPlay(item) },
                onDismiss = callbacks.onDismissOverlay,
            )
        }

        is Overlay.RecordingSheet -> RecordingSheet(
            item = overlay.item,
            header = PlayerLabels.subtitle(overlay.item, env, resources),
            isCurrentAndPlaying = playback.isPlaying && playback.recordingId == overlay.item.id,
            onPlayPause = { callbacks.onSheetPlay(overlay.item) },
            onOpenWith = { callbacks.onSheetOpen(overlay.item, true) },
            onDismiss = callbacks.onDismissOverlay,
        )

        Overlay.DateFilterSheet -> DateFilterSheet(
            current = state.dateFilter,
            onPreset = callbacks.onDateFilterSelected,
            onPickDay = callbacks.onOpenSearchDayPicker,
            onCustomRange = callbacks.onOpenRangePicker,
            onDismiss = callbacks.onDismissOverlay,
        )

        Overlay.HomeDatePicker -> DayPickerDialog(
            initial = state.day,
            today = env.today,
            title = stringResource(R.string.date_picker_title),
            showTodayShortcut = true,
            onConfirm = callbacks.onDayPicked,
            onDismiss = callbacks.onDismissOverlay,
        )

        Overlay.SearchDayPicker -> DayPickerDialog(
            initial = (state.dateFilter as? DateFilter.Day)?.date ?: env.today,
            today = env.today,
            title = stringResource(R.string.date_picker_title),
            showTodayShortcut = false,
            onConfirm = callbacks.onDayPicked,
            onDismiss = callbacks.onDismissOverlay,
        )

        Overlay.RangePicker -> RangePickerDialog(
            initial = state.dateFilter as? DateFilter.Range,
            today = env.today,
            onConfirm = callbacks.onRangePicked,
            onClear = {
                callbacks.onClearDateFilter()
                callbacks.onDismissOverlay()
            },
            onDismiss = callbacks.onDismissOverlay,
        )

        is Overlay.SettingsDialog -> AllowInSettingsDialog(
            group = overlay.group,
            onDismiss = callbacks.onDismissOverlay,
            onOpenSettings = { callbacks.onSettingsDialogConfirm(overlay.group) },
        )
    }
}

/** `Incoming · 1 min 5 s · Tue 3 Nov, 09:15`: the row's meta line plus the stamp. */
private fun callSheetHeader(item: ResultItem, env: DeviceEnv, resources: Resources): String =
    listOf(RowFormatter.format(item, env, resources).meta.text, DateLabels.stamp(item.time, env))
        .filter { it.isNotBlank() }
        .joinToString(" · ")

private fun headerText(group: ListGroup, env: DeviceEnv, resources: Resources): String = when (group) {
    is ListGroup.DateGroup -> DateLabels.header(group.date, env, resources)

    is ListGroup.PartOfDayGroup -> resources.getString(
        when (group.part) {
            PartOfDay.EARLY_HOURS -> R.string.part_early_hours
            PartOfDay.MORNING -> R.string.part_morning
            PartOfDay.AFTERNOON -> R.string.part_afternoon
            PartOfDay.EVENING -> R.string.part_evening
            PartOfDay.NIGHT -> R.string.part_night
        },
    )
}

private const val KEY_INDEXING_BANNER = "banner:indexing"
private const val KEY_PERMISSION_BANNER = "banner:permission"
private const val KEY_APPEND_ERROR = "footer:append_error"
private const val KEY_STATE = "state"
private const val TYPE_BANNER = "banner"
private const val TYPE_ROW = "row"
private const val TYPE_HEADER = "header"
private const val TYPE_FOOTER = "footer"
private const val TYPE_SKELETON = "skeleton"
private const val TYPE_STATE = "state"
