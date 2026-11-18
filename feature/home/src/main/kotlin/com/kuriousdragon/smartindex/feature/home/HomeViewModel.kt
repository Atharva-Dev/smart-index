package com.kuriousdragon.smartindex.feature.home

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.IndexConfig
import com.kuriousdragon.smartindex.core.model.IndexQuery
import com.kuriousdragon.smartindex.core.model.IndexStatus
import com.kuriousdragon.smartindex.core.model.IndexStatusSource
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.PerfTrace
import com.kuriousdragon.smartindex.core.model.PermissionService
import com.kuriousdragon.smartindex.core.model.ResultItem
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.core.model.TypeGating
import com.kuriousdragon.smartindex.feature.home.action.ItemActions
import com.kuriousdragon.smartindex.feature.home.di.HomeDefault
import com.kuriousdragon.smartindex.feature.home.format.PlayerLabels
import com.kuriousdragon.smartindex.feature.home.model.Commit
import com.kuriousdragon.smartindex.feature.home.model.CommitInputs
import com.kuriousdragon.smartindex.feature.home.model.CountsKey
import com.kuriousdragon.smartindex.feature.home.model.DateFilter
import com.kuriousdragon.smartindex.feature.home.model.DeviceEnv
import com.kuriousdragon.smartindex.feature.home.model.DeviceEnvSource
import com.kuriousdragon.smartindex.feature.home.model.Grouping
import com.kuriousdragon.smartindex.feature.home.model.HomeClock
import com.kuriousdragon.smartindex.feature.home.model.HomeConfig
import com.kuriousdragon.smartindex.feature.home.model.ListEntry
import com.kuriousdragon.smartindex.feature.home.model.ListRequest
import com.kuriousdragon.smartindex.feature.home.model.Tab
import com.kuriousdragon.smartindex.feature.home.model.countsKey
import com.kuriousdragon.smartindex.feature.home.model.deriveRequest
import com.kuriousdragon.smartindex.feature.home.pipeline.CountsLoader
import com.kuriousdragon.smartindex.feature.home.pipeline.ListPipeline
import com.kuriousdragon.smartindex.feature.home.pipeline.indexTicks
import com.kuriousdragon.smartindex.feature.home.prefs.HomePrefs
import com.kuriousdragon.smartindex.feature.home.state.BannersUi
import com.kuriousdragon.smartindex.feature.home.state.HomeEffect
import com.kuriousdragon.smartindex.feature.home.state.HomeMode
import com.kuriousdragon.smartindex.feature.home.state.HomeUiState
import com.kuriousdragon.smartindex.feature.home.state.Overlay
import com.kuriousdragon.smartindex.feature.home.state.PERMISSION_BANNER_ORDER
import com.kuriousdragon.smartindex.feature.home.state.RowPlayback
import com.kuriousdragon.smartindex.feature.home.state.TabAccess
import com.kuriousdragon.smartindex.feature.home.state.UiText
import com.kuriousdragon.smartindex.feature.home.state.buildBanners
import com.kuriousdragon.smartindex.feature.player.PlayRequest
import com.kuriousdragon.smartindex.feature.player.PlayerEvent
import com.kuriousdragon.smartindex.feature.player.PlayerPhase
import com.kuriousdragon.smartindex.feature.player.RecordingPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The one ViewModel behind Home and Search (LLD — Home & Search UI §3.3). It turns user intents into
 * commits, exposes [uiState], the paged list, the playback marker and one-shot [effects]. Logs enum and
 * class names only, never the query, titles, names or numbers.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val index: IndexQuery,
    private val statusSource: IndexStatusSource,
    val permissionService: PermissionService,
    private val envSource: DeviceEnvSource,
    private val prefs: HomePrefs,
    private val actions: ItemActions,
    val player: RecordingPlayer,
    private val clock: HomeClock,
    @ApplicationContext context: Context,
    @HomeDefault private val default: CoroutineDispatcher,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val resources = context.resources
    private val pipeline = ListPipeline(index)

    private val restored = savedState.restoreInputs(envSource.env.value.today)
    private val user = MutableStateFlow(restored.first)
    private val query = MutableStateFlow(restored.second)
    private val queryInput = MutableStateFlow(restored.second)
    private val overlay = MutableStateFlow<Overlay>(Overlay.None)
    private val firstPageShown = MutableStateFlow(false)
    private val counts = MutableStateFlow<Map<ItemType, Int>?>(null)
    private val settled = MutableStateFlow(NONE_SETTLED)
    private val effectChannel = Channel<HomeEffect>(Channel.BUFFERED)
    private var pendingCountsTrace: Int? = null
    private var openJob: Job? = null

    /** The current commit with the key of its counts (the counts key can change without a new commit). */
    private val commitWithKey = MutableStateFlow(createCommit(currentInputs(), previous = null))

    /** Hot state of the screen; never emits positions. */
    val uiState: StateFlow<HomeUiState>

    /** Commit, then the pipeline, on [default], cached in the ViewModel scope. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val pages: Flow<PagingData<ListEntry>> = commitWithKey
        .map { it.first }
        .distinctUntilChanged()
        .flatMapLatest { pipeline.pages(it) }
        .flowOn(default)
        .cachedIn(viewModelScope)

    /** The playing recording, distinct (positions never reach it). */
    val rowPlayback: StateFlow<RowPlayback> = player.state
        .map { state ->
            RowPlayback(
                recordingId = if (state.phase == PlayerPhase.CLOSED) null else state.recordingId,
                isPlaying = state.isPlaying,
            )
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RowPlayback(null, false))

    /** One-shot instructions for the UI. */
    val effects: Flow<HomeEffect> = effectChannel.receiveAsFlow()

    init {
        uiState = buildUiState()
        collectCommits()
        collectDebouncedQuery()
        collectCounts()
        collectPlayerEvents()
    }

    // ---- lifecycle ----

    fun onStart() = envSource.refresh()

    /** From FirstPageTraceEffect: unblocks the Search counts, marks the first Timeline page as shown. */
    fun onFirstPageShown(commitId: Int) {
        settled.value = commitId
        val current = commitWithKey.value.first
        if (current.id == commitId && current.request is ListRequest.Timeline) firstPageShown.value = true
    }

    /** SEARCH leaves to HOME (true); an overlay closes (true); otherwise false and the system handles it. */
    fun onBack(): Boolean {
        if (user.value.mode == HomeMode.SEARCH) {
            updateUser { it.copy(mode = HomeMode.HOME) }
            return true
        }
        if (overlay.value != Overlay.None) {
            overlay.value = Overlay.None
            return true
        }
        return false
    }

    /** The Compose side pauses on ON_STOP (unless the change is a configuration change). */
    fun pausePlayback() = player.pause()

    // ---- search ----

    fun onSearchBarClick() {
        query.value = ""
        queryInput.value = ""
        savedState[HomeSavedKeys.QUERY] = ""
        updateUser { it.copy(mode = HomeMode.SEARCH, text = "", filter = DateFilter.AnyDate) }
    }

    fun onQueryChange(text: String) {
        val limited = text.limitQuery()
        query.value = limited
        queryInput.value = limited
        savedState[HomeSavedKeys.QUERY] = limited
    }

    fun onImeSearch() = commitNow(query.value)

    fun onClearQuery() {
        query.value = ""
        queryInput.value = ""
        savedState[HomeSavedKeys.QUERY] = ""
        commitNow("")
    }

    // ---- date ----

    fun onPrevDay() = updateUser { it.copy(day = it.day.minusDays(1)) }

    fun onNextDay() {
        if (!user.value.day.isBefore(envSource.env.value.today)) return
        updateUser { it.copy(day = it.day.plusDays(1)) }
    }

    fun onToday() = updateUser { it.copy(day = envSource.env.value.today) }

    fun onOpenHomeDatePicker() = setOverlay(Overlay.HomeDatePicker)

    /** Home picker OK, or the Search day picker OK (a day filter). A date after today is ignored. */
    fun onDayPicked(date: LocalDate) {
        if (date.isAfter(envSource.env.value.today)) return
        val searchPicker = overlay.value == Overlay.SearchDayPicker
        overlay.value = Overlay.None
        updateUser { if (searchPicker) it.copy(filter = DateFilter.Day(date)) else it.copy(day = date) }
    }

    fun onOpenDateSheet() = setOverlay(Overlay.DateFilterSheet)

    fun onDateFilterSelected(f: DateFilter) {
        overlay.value = Overlay.None
        updateUser { it.copy(filter = f) }
    }

    fun onClearDateFilter() = updateUser { it.copy(filter = DateFilter.AnyDate) }

    fun onOpenSearchDayPicker() = setOverlay(Overlay.SearchDayPicker)

    fun onOpenRangePicker() = setOverlay(Overlay.RangePicker)

    /** [end] == null: a single day. */
    fun onRangePicked(start: LocalDate, end: LocalDate?) {
        overlay.value = Overlay.None
        val filter = when {
            end == null || end == start -> DateFilter.Day(start)
            end.isBefore(start) -> DateFilter.Range(end, start)
            else -> DateFilter.Range(start, end)
        }
        updateUser { it.copy(filter = filter) }
    }

    // ---- tabs, empty-state actions ----

    fun onTabSelected(tab: Tab) = updateUser { it.copy(tab = tab) }

    fun onShowAllTypes() = onTabSelected(Tab.ALL)

    // ---- rows ----

    fun onRowClick(item: ResultItem) {
        when (item.type) {
            ItemType.CALL -> overlay.value = Overlay.CallSheet(item)
            ItemType.RECORDING -> overlay.value = Overlay.RecordingSheet(item)
            ItemType.MESSAGE -> launchOpen { actions.openMessage(item) }
            else -> launchOpen { actions.open(item, chooser = false) }
        }
    }

    fun onRowMoreClick(item: ResultItem) {
        overlay.value = Overlay.FileSheet(item)
    }

    fun onPlayClick(item: ResultItem) = play(item)

    fun onSheetOpen(item: ResultItem, chooser: Boolean) {
        overlay.value = Overlay.None
        launchOpen { actions.open(item, chooser) }
    }

    fun onSheetCallBack(item: ResultItem) {
        overlay.value = Overlay.None
        actions.callBack(item)?.let(::send)
    }

    fun onSheetOpenContact(item: ResultItem) {
        overlay.value = Overlay.None
        launchSafely { send(actions.openContact(item)) }
    }

    fun onSheetAddContact(item: ResultItem) {
        overlay.value = Overlay.None
        actions.addContact(item)?.let(::send)
    }

    fun onSheetPlay(item: ResultItem) {
        overlay.value = Overlay.None
        play(item)
    }

    // ---- permissions and banners ----

    /** The first denied group in banner order; permanently denied (never FILES) opens the Settings dialog. */
    fun onBannerAllowAccess() {
        val states = permissionService.state.value
        val group = PERMISSION_BANNER_ORDER.firstOrNull {
            states[it] == GroupState.DENIED || states[it] == GroupState.DENIED_PERMANENTLY
        } ?: return
        requestGroup(group)
    }

    fun onBannerNotNow() {
        launchSafely { prefs.snoozePermissionBanner() }
    }

    fun onTabAllowAccess(tab: Tab) {
        tab.group?.let { send(HomeEffect.RequestPermission(it)) }
    }

    fun onTabOpenAppSettings(tab: Tab) {
        tab.group?.let(::launchSettings)
    }

    fun onSettingsDialogConfirm(group: Group) {
        launchSettings(group)
        overlay.value = Overlay.None
    }

    /** The permission state flow updates by itself; only a class-name log line remains. */
    fun onPermissionResult(group: Group, state: GroupState) {
        SiLog.d(TAG, "permission result group=${group.name} state=${state.name}")
    }

    // ---- misc ----

    fun onOverflowStatus() = send(HomeEffect.OpenStatus(null))

    fun onOverflowPermissions() = send(HomeEffect.OpenStatus(PERMISSIONS_SECTION))

    fun onDismissOverlay() {
        overlay.value = Overlay.None
    }

    override fun onCleared() {
        player.close()
        super.onCleared()
    }

    // ---- state ----

    private fun buildUiState(): StateFlow<HomeUiState> {
        val local = combine(user, query, overlay, firstPageShown, counts, ::LocalInputs)
        val external = combine(
            permissionService.state,
            envSource.env,
            statusSource.status,
            prefs.permissionBannerSnoozedUntil.onStart { emit(Long.MAX_VALUE) },
            ::ExternalInputs,
        )
        val initial = stateOf(
            LocalInputs(user.value, query.value, overlay.value, false, null),
            ExternalInputs(
                permissionService.state.value,
                envSource.env.value,
                statusSource.status.value,
                Long.MAX_VALUE,
            ),
            commitWithKey.value.first,
        )
        return combine(local, external, commitWithKey) { l, e, c -> stateOf(l, e, c.first) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)
    }

    private fun stateOf(l: LocalInputs, e: ExternalInputs, commit: Commit): HomeUiState {
        val u = l.user
        val access = Tab.entries.associateWith { tab ->
            val group = tab.group
            if (group != null && !TypeGating.isUsable(e.groups[group])) {
                TabAccess.Locked(group, e.groups[group] ?: GroupState.DENIED)
            } else {
                TabAccess.Open
            }
        }
        val banners: BannersUi = buildBanners(e.status, e.groups, u.tab, e.snoozedUntil, clock.nowMs())
        return HomeUiState(
            mode = u.mode,
            env = e.env,
            day = u.day,
            query = l.query,
            dateFilter = u.filter,
            tab = u.tab,
            counts = l.counts,
            tabAccess = access,
            commit = commit,
            banners = banners,
            overlay = l.overlay,
            isCatchUp = e.status.isCatchUp,
            firstPageShown = l.firstPageShown,
        )
    }

    private fun currentInputs(): CommitInputs = inputsOf(user.value, permissionService.state.value, envSource.env.value)

    private fun inputsOf(u: UserInputs, groups: Map<Group, GroupState>, env: DeviceEnv) =
        CommitInputs(u.mode, u.day, u.text, u.filter, u.tab, groups, env)

    // ---- commits ----

    private fun collectCommits() {
        viewModelScope.launch {
            combine(user, permissionService.state, envSource.env, ::inputsOf).collect(::evaluate)
        }
    }

    /** A new [Commit] only when the request, the grouping or the zone changes; else maybe a new counts key. */
    private fun evaluate(inputs: CommitInputs) {
        val (current, key) = commitWithKey.value
        val request = deriveRequest(inputs)
        if (request != current.request || groupingOf(request) != current.grouping ||
            inputs.env.zone != current.env.zone
        ) {
            commitWithKey.value = createCommit(inputs, current)
        } else if (countsKey(inputs) != key) {
            commitWithKey.value = current to countsKey(inputs)
        }
    }

    private fun groupingOf(request: ListRequest) =
        if (request is ListRequest.Timeline) Grouping.PART_OF_DAY else Grouping.DATE

    /** Trace sections per LLD §5.8: Search always, Timeline for a new day or the first commit, never Hint/Locked. */
    private fun createCommit(inputs: CommitInputs, previous: Commit?): Pair<Commit, CountsKey?> {
        val request = deriveRequest(inputs)
        val previousRequest = previous?.request
        val section = when (request) {
            is ListRequest.Search -> PerfTrace.SEARCH_FIRST_PAGE

            is ListRequest.Timeline ->
                if (previousRequest is ListRequest.Timeline && previousRequest.day == request.day) {
                    null
                } else {
                    PerfTrace.TIMELINE_FIRST_PAGE
                }

            else -> null
        }
        val id = PerfTrace.nextCookie()
        section?.let { HomeTrace.sink.begin(it, id) }
        if (request is ListRequest.Search) {
            HomeTrace.sink.begin(PerfTrace.SEARCH_COUNTS, id)
            pendingCountsTrace = id
        }
        val commit = Commit(id, request, groupingOf(request), inputs.env, section)
        SiLog.d(TAG, "commit id=$id request=${request::class.simpleName} section=${section != null}")
        return commit to countsKey(inputs)
    }

    // ---- query ----

    private fun String.limitQuery(): String {
        if (length <= HomeConfig.MAX_QUERY_CHARS) return this
        val cut = take(HomeConfig.MAX_QUERY_CHARS)
        return if (cut.last().isHighSurrogate()) cut.dropLast(1) else cut
    }

    @OptIn(FlowPreview::class)
    private fun collectDebouncedQuery() {
        viewModelScope.launch {
            queryInput.debounce(IndexConfig.SEARCH_DEBOUNCE_MS)
                .map { it.trim() }
                .distinctUntilChanged()
                .collect { text -> updateUser { it.copy(text = text) } }
        }
    }

    private fun commitNow(raw: String) = updateUser { it.copy(text = raw.trim()) }

    private fun updateUser(transform: (UserInputs) -> UserInputs) {
        user.update(transform)
        savedState.persist(user.value)
    }

    private fun setOverlay(value: Overlay) {
        overlay.value = value
    }

    // ---- counts ----

    private fun collectCounts() {
        viewModelScope.launch {
            CountsLoader(index).counts(commitWithKey, settled, indexTicks(statusSource.status))
                .collect { result ->
                    counts.value = result.counts
                    if (result.counts != null && result.commitId == pendingCountsTrace) {
                        pendingCountsTrace = null
                        HomeTrace.sink.end(PerfTrace.SEARCH_COUNTS, result.commitId)
                    }
                }
        }
    }

    // ---- actions, permissions ----

    private fun send(effect: HomeEffect) {
        effectChannel.trySend(effect)
    }

    /** One open at a time: further row taps are dropped while an open is running. */
    private fun launchOpen(block: suspend () -> HomeEffect) {
        if (openJob?.isActive == true) return
        openJob = launchSafely { handle(block()) }
    }

    private fun handle(effect: HomeEffect) {
        if (effect is HomeEffect.RequestPermission) requestGroup(effect.group) else send(effect)
    }

    private fun requestGroup(group: Group) {
        val permanent = permissionService.state.value[group] == GroupState.DENIED_PERMANENTLY
        if (permanent && group != Group.FILES) {
            overlay.value = Overlay.SettingsDialog(group)
        } else {
            send(HomeEffect.RequestPermission(group))
        }
    }

    private fun launchSettings(group: Group) {
        send(
            HomeEffect.Launch(
                attempts = listOf(permissionService.settingsIntent(group)),
                noHandler = UiText.Res(R.string.err_settings_open),
            ),
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private fun launchSafely(block: suspend () -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            SiLog.w(TAG, "action failed", t)
        }
    }

    // ---- playback ----

    private fun play(item: ResultItem) {
        val recordingId = if (item.type == ItemType.RECORDING) item.id else item.linkedRecordingId ?: return
        val state = player.state.value
        when {
            state.recordingId == recordingId &&
                (state.phase == PlayerPhase.PLAYING || state.phase == PlayerPhase.PAUSED) ->
                player.togglePlayPause()

            state.recordingId == recordingId && state.phase == PlayerPhase.ERROR -> player.retry()

            else -> player.play(
                PlayRequest(
                    recordingId,
                    PlayerLabels.title(item),
                    PlayerLabels.subtitle(item, envSource.env.value, resources),
                ),
            )
        }
    }

    private fun collectPlayerEvents() {
        viewModelScope.launch {
            player.events.collect { event ->
                if (event is PlayerEvent.Failed) {
                    send(HomeEffect.ShowMessage(UiText.Res(R.string.err_play), long = true))
                }
            }
        }
    }

    private class LocalInputs(
        val user: UserInputs,
        val query: String,
        val overlay: Overlay,
        val firstPageShown: Boolean,
        val counts: Map<ItemType, Int>?,
    )

    private class ExternalInputs(
        val groups: Map<Group, GroupState>,
        val env: DeviceEnv,
        val status: IndexStatus,
        val snoozedUntil: Long,
    )

    private companion object {
        const val TAG = "Home"
        const val STOP_TIMEOUT_MS = 5_000L
        const val NONE_SETTLED = -1
        const val PERMISSIONS_SECTION = "permissions"
    }
}
