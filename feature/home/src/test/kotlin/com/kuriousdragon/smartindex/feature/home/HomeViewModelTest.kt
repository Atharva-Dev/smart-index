package com.kuriousdragon.smartindex.feature.home

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.DateRange
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.IndexPhase
import com.kuriousdragon.smartindex.core.model.IndexStats
import com.kuriousdragon.smartindex.core.model.IndexStatus
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.core.model.OpenTarget
import com.kuriousdragon.smartindex.core.model.PerfTrace
import com.kuriousdragon.smartindex.core.model.SearchQuery
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.feature.home.action.ItemActions
import com.kuriousdragon.smartindex.feature.home.model.CountsKey
import com.kuriousdragon.smartindex.feature.home.model.DateFilter
import com.kuriousdragon.smartindex.feature.home.model.Grouping
import com.kuriousdragon.smartindex.feature.home.model.HomeClock
import com.kuriousdragon.smartindex.feature.home.model.ListRequest
import com.kuriousdragon.smartindex.feature.home.model.Tab
import com.kuriousdragon.smartindex.feature.home.model.resolve
import com.kuriousdragon.smartindex.feature.home.model.toDateRange
import com.kuriousdragon.smartindex.feature.home.prefs.HomePrefs
import com.kuriousdragon.smartindex.feature.home.state.HomeEffect
import com.kuriousdragon.smartindex.feature.home.state.HomeMode
import com.kuriousdragon.smartindex.feature.home.state.HomeUiState
import com.kuriousdragon.smartindex.feature.home.state.Overlay
import com.kuriousdragon.smartindex.feature.home.state.RowPlayback
import com.kuriousdragon.smartindex.feature.home.state.TabAccess
import com.kuriousdragon.smartindex.feature.home.state.UiText
import com.kuriousdragon.smartindex.feature.home.testing.FakeContactLookup
import com.kuriousdragon.smartindex.feature.home.testing.FakeDeviceEnvSource
import com.kuriousdragon.smartindex.feature.home.testing.FakeIndexControl
import com.kuriousdragon.smartindex.feature.home.testing.FakeIndexQuery
import com.kuriousdragon.smartindex.feature.home.testing.FakeIndexStatusSource
import com.kuriousdragon.smartindex.feature.home.testing.FakeItemUriResolver
import com.kuriousdragon.smartindex.feature.home.testing.FakePermissionService
import com.kuriousdragon.smartindex.feature.home.testing.FakeRecordingPlayer
import com.kuriousdragon.smartindex.feature.home.testing.TestEnv
import com.kuriousdragon.smartindex.feature.home.testing.call
import com.kuriousdragon.smartindex.feature.home.testing.document
import com.kuriousdragon.smartindex.feature.home.testing.message
import com.kuriousdragon.smartindex.feature.home.testing.photo
import com.kuriousdragon.smartindex.feature.home.testing.recording
import com.kuriousdragon.smartindex.feature.player.PlayerEvent
import com.kuriousdragon.smartindex.feature.player.PlayerPhase
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HomeViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val index = FakeIndexQuery()
    private val status = FakeIndexStatusSource()
    private val perms = FakePermissionService()
    private val envSource = FakeDeviceEnvSource()
    private val player = FakeRecordingPlayer()
    private val resolver = FakeItemUriResolver()
    private val control = FakeIndexControl()
    private val contacts = FakeContactLookup()
    private val actions = ItemActions(resolver, index, contacts, control)
    private val trace = RecordingTrace()
    private val logged = mutableListOf<String>()
    private val oldSink = SiLog.sink
    private val oldTrace = HomeTrace.sink
    private val now = 1_800_000_000_000L
    private val storeScope = CoroutineScope(dispatcher + SupervisorJob())
    private val effects = mutableListOf<HomeEffect>()
    private val store = ViewModelStore()

    private val today = TestEnv.today
    private val saved = mutableMapOf<String, Any?>()
    private lateinit var vm: HomeViewModel
    private lateinit var states: List<HomeUiState>

    private class RecordingTrace : HomeTrace.Sink {
        val begins = mutableListOf<Pair<String, Int>>()
        val ends = mutableListOf<Pair<String, Int>>()

        override fun begin(name: String, cookie: Int) {
            begins += name to cookie
        }

        override fun end(name: String, cookie: Int) {
            ends += name to cookie
        }

        fun begun(name: String) = begins.filter { it.first == name }.map { it.second }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        SiLog.sink = SiLog.Sink { _, _, message -> logged += message }
        HomeTrace.sink = trace
    }

    @After
    fun tearDown() {
        store.clear()
        storeScope.cancel()
        HomeTrace.sink = oldTrace
        SiLog.sink = oldSink
        Dispatchers.resetMain()
    }

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    /** Builds the ViewModel, keeps `uiState` and `effects` collected and lets the first work run. */
    private fun TestScope.start(snapshot: Map<String, Any?> = saved): HomeViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = PreferenceDataStoreFactory.create(scope = storeScope) {
            File(tmp.root, "sidx_app.preferences_pb")
        }
        val model = HomeViewModel(
            index, status, perms, envSource, HomePrefs(dataStore, HomeClock { now }), actions, player,
            HomeClock { now }, context, dispatcher, SavedStateHandle(snapshot),
        )
        vm = model
        val seen = mutableListOf<HomeUiState>()
        states = seen
        backgroundScope.launch { model.uiState.collect { seen += it } }
        backgroundScope.launch { model.effects.collect { effects += it } }
        runCurrent()
        return model
    }

    private val ui get() = vm.uiState.value
    private val request get() = ui.commit.request

    private fun TestScope.settle() {
        advanceTimeBy(DEBOUNCE + 1)
        runCurrent()
    }

    private fun TestScope.type(text: String) {
        vm.onQueryChange(text)
        runCurrent()
    }

    private fun search(text: String, range: DateRange? = null, type: ItemType? = null) =
        ListRequest.Search(SearchQuery(text, range, type))

    // ---- commits -------------------------------------------------------------------------------

    @Test
    fun `commit on start is the Home timeline of today for the ALL tab`() = test {
        start()
        assertThat(ui.mode).isEqualTo(HomeMode.HOME)
        assertThat(ui.day).isEqualTo(today)
        assertThat(ui.tab).isEqualTo(Tab.ALL)
        assertThat(request).isEqualTo(ListRequest.Timeline(today, null))
        assertThat(ui.commit.grouping).isEqualTo(Grouping.PART_OF_DAY)
        assertThat(ui.commit.traceSection).isEqualTo(PerfTrace.TIMELINE_FIRST_PAGE)
        assertThat(trace.begins).containsExactly(PerfTrace.TIMELINE_FIRST_PAGE to ui.commit.id)
    }

    @Test
    fun `typing ra then rah within the debounce creates one Search commit`() = test {
        start()
        vm.onSearchBarClick()
        runCurrent()
        type("ra")
        advanceTimeBy(100)
        type("rah")
        settle()
        assertThat(request).isEqualTo(search("rah"))
        assertThat(trace.begun(PerfTrace.SEARCH_FIRST_PAGE)).hasSize(1)
        assertThat(ui.commit.grouping).isEqualTo(Grouping.DATE)
        assertThat(ui.query).isEqualTo("rah")
    }

    @Test
    fun `the commit waits for the full debounce`() = test {
        start()
        vm.onSearchBarClick()
        type("ra")
        advanceTimeBy(DEBOUNCE - 1)
        runCurrent()
        assertThat(request).isEqualTo(ListRequest.Hint)
        advanceTimeBy(2)
        runCurrent()
        assertThat(request).isEqualTo(search("ra"))
    }

    @Test
    fun `IME search commits at once`() = test {
        start()
        vm.onSearchBarClick()
        type("  ra ")
        vm.onImeSearch()
        runCurrent()
        assertThat(request).isEqualTo(search("ra"))
        assertThat(trace.begun(PerfTrace.SEARCH_FIRST_PAGE)).hasSize(1)
    }

    @Test
    fun `clearing the query commits at once and returns to the hint`() = test {
        start()
        vm.onSearchBarClick()
        type("ra")
        settle()
        vm.onClearQuery()
        runCurrent()
        assertThat(ui.query).isEmpty()
        assertThat(request).isEqualTo(ListRequest.Hint)
    }

    @Test
    fun `the same trimmed text creates no commit`() = test {
        start()
        vm.onSearchBarClick()
        type("ra")
        settle()
        val id = ui.commit.id
        type("ra  ")
        settle()
        vm.onImeSearch()
        runCurrent()
        assertThat(ui.commit.id).isEqualTo(id)
        assertThat(trace.begun(PerfTrace.SEARCH_FIRST_PAGE)).hasSize(1)
    }

    @Test
    fun `blank text with any date is the hint and creates no trace section`() = test {
        start()
        vm.onSearchBarClick()
        runCurrent()
        assertThat(request).isEqualTo(ListRequest.Hint)
        assertThat(ui.commit.traceSection).isNull()
        assertThat(trace.begins.map { it.first }).containsExactly(PerfTrace.TIMELINE_FIRST_PAGE)
    }

    @Test
    fun `a date only search is a Search commit`() = test {
        start()
        vm.onSearchBarClick()
        vm.onDateFilterSelected(DateFilter.Last7Days)
        runCurrent()
        val range = DateFilter.Last7Days.resolve(today)!!.toDateRange(TestEnv.zone)
        assertThat(request).isEqualTo(search("", range))
        assertThat(ui.overlay).isEqualTo(Overlay.None)
    }

    @Test
    fun `the query is cut to 200 characters`() = test {
        start()
        vm.onSearchBarClick()
        type("x".repeat(250))
        assertThat(ui.query).hasLength(200)
        settle()
        assertThat((request as ListRequest.Search).query.text).hasLength(200)
    }

    @Test
    fun `entering Search resets query and filter and keeps the tab`() = test {
        start()
        vm.onTabSelected(Tab.PHOTOS)
        vm.onSearchBarClick()
        vm.onDateFilterSelected(DateFilter.Today)
        type("ra")
        settle()
        vm.onBack()
        runCurrent()
        vm.onSearchBarClick()
        runCurrent()
        assertThat(ui.mode).isEqualTo(HomeMode.SEARCH)
        assertThat(ui.query).isEmpty()
        assertThat(ui.dateFilter).isEqualTo(DateFilter.AnyDate)
        assertThat(ui.tab).isEqualTo(Tab.PHOTOS)
        assertThat(request).isEqualTo(ListRequest.Hint)
    }

    @Test
    fun `back from Search returns to Home with the old day and keeps the tab`() = test {
        start()
        vm.onPrevDay()
        vm.onTabSelected(Tab.VIDEOS)
        vm.onSearchBarClick()
        runCurrent()
        assertThat(vm.onBack()).isTrue()
        runCurrent()
        assertThat(ui.mode).isEqualTo(HomeMode.HOME)
        assertThat(request).isEqualTo(ListRequest.Timeline(today.minusDays(1), ItemType.VIDEO))
        assertThat(ui.tab).isEqualTo(Tab.VIDEOS)
    }

    @Test
    fun `back closes an overlay and otherwise lets the system handle it`() = test {
        start()
        assertThat(vm.onBack()).isFalse()
        vm.onOpenDateSheet()
        runCurrent()
        assertThat(ui.overlay).isEqualTo(Overlay.DateFilterSheet)
        assertThat(vm.onBack()).isTrue()
        runCurrent()
        assertThat(ui.overlay).isEqualTo(Overlay.None)
        assertThat(vm.onBack()).isFalse()
    }

    // ---- days ----------------------------------------------------------------------------------

    @Test
    fun `day buttons keep the tab and a future day is ignored`() = test {
        start()
        vm.onTabSelected(Tab.DOCUMENTS)
        vm.onNextDay()
        runCurrent()
        assertThat(ui.day).isEqualTo(today)
        assertThat(ui.canGoNext).isFalse()
        vm.onPrevDay()
        vm.onPrevDay()
        runCurrent()
        assertThat(ui.day).isEqualTo(today.minusDays(2))
        assertThat(ui.canGoNext).isTrue()
        assertThat(request).isEqualTo(ListRequest.Timeline(today.minusDays(2), ItemType.DOCUMENT))
        vm.onNextDay()
        runCurrent()
        assertThat(ui.day).isEqualTo(today.minusDays(1))
        vm.onDayPicked(today.plusDays(1))
        runCurrent()
        assertThat(ui.day).isEqualTo(today.minusDays(1))
        vm.onDayPicked(today.minusDays(9))
        vm.onToday()
        runCurrent()
        assertThat(ui.day).isEqualTo(today)
        assertThat(ui.tab).isEqualTo(Tab.DOCUMENTS)
    }

    @Test
    fun `a day change begins a timeline trace and a tab only change does not`() = test {
        start()
        val first = trace.begins.size
        vm.onTabSelected(Tab.PHOTOS)
        runCurrent()
        assertThat(ui.commit.traceSection).isNull()
        assertThat(trace.begins).hasSize(first)
        vm.onPrevDay()
        runCurrent()
        assertThat(ui.commit.traceSection).isEqualTo(PerfTrace.TIMELINE_FIRST_PAGE)
        assertThat(trace.begins).hasSize(first + 1)
    }

    @Test
    fun `the Search day picker sets a day filter and the Home picker sets the day`() = test {
        start()
        vm.onOpenHomeDatePicker()
        runCurrent()
        assertThat(ui.overlay).isEqualTo(Overlay.HomeDatePicker)
        vm.onDayPicked(today.minusDays(3))
        runCurrent()
        assertThat(ui.day).isEqualTo(today.minusDays(3))
        assertThat(ui.overlay).isEqualTo(Overlay.None)
        vm.onSearchBarClick()
        vm.onOpenDateSheet()
        vm.onOpenSearchDayPicker()
        runCurrent()
        assertThat(ui.overlay).isEqualTo(Overlay.SearchDayPicker)
        vm.onDayPicked(today.minusDays(1))
        runCurrent()
        assertThat(ui.dateFilter).isEqualTo(DateFilter.Day(today.minusDays(1)))
        assertThat(ui.day).isEqualTo(today.minusDays(3))
        vm.onClearDateFilter()
        runCurrent()
        assertThat(ui.dateFilter).isEqualTo(DateFilter.AnyDate)
    }

    @Test
    fun `range picker end null is a single day and a range keeps both ends`() = test {
        start()
        vm.onSearchBarClick()
        vm.onOpenRangePicker()
        runCurrent()
        assertThat(ui.overlay).isEqualTo(Overlay.RangePicker)
        vm.onRangePicked(today.minusDays(4), null)
        runCurrent()
        assertThat(ui.dateFilter).isEqualTo(DateFilter.Day(today.minusDays(4)))
        vm.onRangePicked(today.minusDays(4), today.minusDays(2))
        runCurrent()
        assertThat(ui.dateFilter).isEqualTo(DateFilter.Range(today.minusDays(4), today.minusDays(2)))
        assertThat(ui.overlay).isEqualTo(Overlay.None)
        vm.onDismissOverlay()
        runCurrent()
        assertThat(ui.overlay).isEqualTo(Overlay.None)
    }

    @Test
    fun `onStart refreshes the device environment`() = test {
        start()
        vm.onStart()
        assertThat(envSource.refreshes).isEqualTo(1)
    }

    // ---- locked tabs ---------------------------------------------------------------------------

    @Test
    fun `a locked tab is a Locked request and granting the group makes it a Timeline`() = test {
        perms.set(Group.CALL_LOG, GroupState.DENIED)
        start()
        assertThat(ui.tabAccess[Tab.CALLS]).isEqualTo(TabAccess.Locked(Group.CALL_LOG, GroupState.DENIED))
        assertThat(ui.tabAccess[Tab.ALL]).isEqualTo(TabAccess.Open)
        assertThat(ui.tabAccess[Tab.PHOTOS]).isEqualTo(TabAccess.Open)
        val begins = trace.begins.size
        vm.onTabSelected(Tab.CALLS)
        runCurrent()
        assertThat(request).isEqualTo(ListRequest.Locked(Tab.CALLS, Group.CALL_LOG))
        assertThat(ui.commit.traceSection).isNull()
        assertThat(trace.begins).hasSize(begins)
        perms.set(Group.CALL_LOG, GroupState.GRANTED)
        runCurrent()
        assertThat(request).isEqualTo(ListRequest.Timeline(today, ItemType.CALL))
        assertThat(ui.tabAccess[Tab.CALLS]).isEqualTo(TabAccess.Open)
    }

    @Test
    fun `a partial group is usable`() = test {
        perms.set(Group.PHOTOS_VIDEOS, GroupState.PARTIAL)
        start()
        vm.onTabSelected(Tab.PHOTOS)
        runCurrent()
        assertThat(request).isEqualTo(ListRequest.Timeline(today, ItemType.PHOTO))
    }

    // ---- paging and cancellation ---------------------------------------------------------------

    @Test
    fun `pages follow the commit`() = test {
        start()
        backgroundScope.launch { vm.pages.collect { } }
        runCurrent()
        assertThat(index.timelineCalls).containsExactly(today to null)
        vm.onTabSelected(Tab.PHOTOS)
        runCurrent()
        assertThat(index.timelineCalls.last()).isEqualTo(today to ItemType.PHOTO)
        vm.onSearchBarClick()
        type("ra")
        settle()
        assertThat(index.searchCalls).containsExactly(SearchQuery("ra", null, ItemType.PHOTO))
    }

    @Test
    fun `a held first query is cancelled by the second commit`() = test {
        index.holdSearchPages = true
        start()
        backgroundScope.launch { vm.pages.collect { } }
        vm.onSearchBarClick()
        type("ra")
        settle()
        type("rah")
        settle()
        assertThat(index.searchCalls.map { it.text }).containsExactly("ra", "rah").inOrder()
        index.heldPage.trySend(listOf(photo(1)))
        index.heldPage.trySend(listOf(photo(2)))
        runCurrent()
        // Only the second (live) query consumed a page: the cancelled first one never received.
        assertThat(index.heldPage.tryReceive().getOrNull()?.map { it.id }).containsExactly(2L)
    }

    // ---- counts --------------------------------------------------------------------------------

    private fun photoCounts(n: Int) = ItemType.entries.associateWith { if (it == ItemType.PHOTO) n else 0 }

    @Test
    fun `counts are null on a key change and kept on an index tick`() = test {
        index.dayCounts = { photoCounts(3) }
        start()
        runCurrent()
        assertThat(ui.counts).isEqualTo(photoCounts(3))
        val gate = CompletableDeferred<Unit>()
        index.countsGate = { gate.await() }
        vm.onPrevDay()
        runCurrent()
        assertThat(ui.counts).isNull()
        gate.complete(Unit)
        runCurrent()
        assertThat(ui.counts).isEqualTo(photoCounts(3))

        // A tab change keeps the key, so the counts stay.
        val before = states.size
        index.dayCounts = { photoCounts(4) }
        vm.onTabSelected(Tab.PHOTOS)
        runCurrent()
        assertThat(states.drop(before).map { it.counts }).doesNotContain(null)

        // An indexing tick recomputes without hiding the badges first.
        val seen = states.size
        status.mutable.value = IndexStatus(stats = IndexStats(photoCounts(5), 0, 5))
        advanceTimeBy(1_100)
        runCurrent()
        assertThat(states.drop(seen).map { it.counts }).doesNotContain(null)
        assertThat(ui.counts).isEqualTo(photoCounts(4))
    }

    @Test
    fun `Search counts wait for the first page`() = test {
        index.queryCounts = { photoCounts(2) }
        start()
        vm.onSearchBarClick()
        type("ra")
        settle()
        assertThat(index.countsCalls.filterIsInstance<CountsKey.Query>())
            .isEmpty()
        vm.onFirstPageShown(ui.commit.id)
        runCurrent()
        assertThat(ui.counts).isEqualTo(photoCounts(2))
        assertThat(trace.ends).containsExactly(PerfTrace.SEARCH_COUNTS to ui.commit.id)
        assertThat(trace.begun(PerfTrace.SEARCH_COUNTS)).containsExactly(ui.commit.id)
    }

    @Test
    fun `Search counts load after 1500 ms without a first page`() = test {
        index.queryCounts = { photoCounts(2) }
        start()
        vm.onSearchBarClick()
        type("rah")
        vm.onImeSearch()
        runCurrent()
        advanceTimeBy(1_499)
        runCurrent()
        assertThat(ui.counts).isNull()
        advanceTimeBy(2)
        runCurrent()
        assertThat(ui.counts).isEqualTo(photoCounts(2))
    }

    @Test
    fun `Short search counts keep waiting past 1500 ms and start at the first page`() = test {
        index.queryCounts = { photoCounts(2) }
        start()
        vm.onSearchBarClick()
        type("ra")
        vm.onImeSearch()
        runCurrent()
        advanceTimeBy(20_000)
        runCurrent()
        assertThat(ui.counts).isNull()
        assertThat(index.countsCalls.filterIsInstance<CountsKey.Query>()).isEmpty()
        vm.onFirstPageShown(ui.commit.id)
        runCurrent()
        assertThat(ui.counts).isEqualTo(photoCounts(2))
    }

    @Test
    fun `counts are hidden for the hint`() = test {
        index.dayCounts = { photoCounts(3) }
        start()
        assertThat(ui.counts).isNotNull()
        vm.onSearchBarClick()
        runCurrent()
        assertThat(ui.counts).isNull()
    }

    @Test
    fun `a superseded Search commit never ends its counts section`() = test {
        index.queryCounts = { photoCounts(1) }
        start()
        vm.onSearchBarClick()
        type("ra")
        settle()
        val first = ui.commit.id
        type("rah")
        settle()
        val second = ui.commit.id
        vm.onFirstPageShown(second)
        runCurrent()
        assertThat(trace.begun(PerfTrace.SEARCH_COUNTS)).containsExactly(first, second).inOrder()
        assertThat(trace.ends).containsExactly(PerfTrace.SEARCH_COUNTS to second)
    }

    @Test
    fun `a failing counts call hides the badges`() = test {
        index.countsGate = { throw IllegalStateException("boom") }
        start()
        assertThat(ui.counts).isNull()
    }

    @Test
    fun `firstPageShown is set by the first Timeline page`() = test {
        start()
        assertThat(ui.firstPageShown).isFalse()
        vm.onFirstPageShown(ui.commit.id)
        runCurrent()
        assertThat(ui.firstPageShown).isTrue()
    }

    // ---- state persistence ---------------------------------------------------------------------

    @Test
    fun `state is restored from the saved state handle`() = test {
        val handle = SavedStateHandle(
            mapOf(
                "mode" to "SEARCH",
                "day" to today.minusDays(2).toEpochDay(),
                "query" to "ra",
                "filter" to "R:${today.minusDays(5).toEpochDay()}:${today.minusDays(1).toEpochDay()}",
                "tab" to "PHOTOS",
            ),
        )
        startWith(handle)
        assertThat(ui.mode).isEqualTo(HomeMode.SEARCH)
        assertThat(ui.day).isEqualTo(today.minusDays(2))
        assertThat(ui.query).isEqualTo("ra")
        assertThat(ui.dateFilter).isEqualTo(DateFilter.Range(today.minusDays(5), today.minusDays(1)))
        assertThat(ui.tab).isEqualTo(Tab.PHOTOS)
        assertThat((request as ListRequest.Search).query.text).isEqualTo("ra")
    }

    @Test
    fun `unparsable saved values fall back to the defaults`() = test {
        startWith(
            SavedStateHandle(
                mapOf("mode" to "WAT", "day" to "yesterday", "query" to 5, "filter" to "R:1", "tab" to "NOPE"),
            ),
        )
        assertThat(ui.mode).isEqualTo(HomeMode.HOME)
        assertThat(ui.day).isEqualTo(today)
        assertThat(ui.query).isEmpty()
        assertThat(ui.dateFilter).isEqualTo(DateFilter.AnyDate)
        assertThat(ui.tab).isEqualTo(Tab.ALL)
    }

    @Test
    fun `a future saved day and filter dates are clamped to today`() = test {
        startWith(
            SavedStateHandle(
                mapOf(
                    "day" to today.plusDays(9).toEpochDay(),
                    "filter" to "D:${today.plusDays(3).toEpochDay()}",
                ),
            ),
        )
        assertThat(ui.day).isEqualTo(today)
        assertThat(ui.dateFilter).isEqualTo(DateFilter.Day(today))
    }

    @Test
    fun `every preset filter survives a round trip through the saved state`() = test {
        val handle = SavedStateHandle()
        startWith(handle)
        vm.onSearchBarClick()
        listOf(
            DateFilter.Today,
            DateFilter.Yesterday,
            DateFilter.Last7Days,
            DateFilter.Last30Days,
            DateFilter.Day(today.minusDays(1)),
            DateFilter.Range(today.minusDays(3), today),
        ).forEach { filter ->
            vm.onDateFilterSelected(filter)
            runCurrent()
            assertThat(decodeFilter(handle.get<String>("filter"), today)).isEqualTo(filter)
        }
        vm.onTabSelected(Tab.MESSAGES)
        vm.onPrevDay()
        type("ra")
        runCurrent()
        assertThat(handle.get<String>("mode")).isEqualTo("SEARCH")
        assertThat(handle.get<String>("tab")).isEqualTo("MESSAGES")
        assertThat(handle.get<Long>("day")).isEqualTo(today.minusDays(1).toEpochDay())
        assertThat(handle.get<String>("query")).isEqualTo("ra")
    }

    private fun TestScope.startWith(handle: SavedStateHandle) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = PreferenceDataStoreFactory.create(scope = storeScope) {
            File(tmp.root, "sidx_app.preferences_pb")
        }
        vm = HomeViewModel(
            index, status, perms, envSource, HomePrefs(dataStore, HomeClock { now }), actions, player,
            HomeClock { now }, context, dispatcher, handle,
        )
        val seen = mutableListOf<HomeUiState>()
        states = seen
        backgroundScope.launch { vm.uiState.collect { seen += it } }
        backgroundScope.launch { vm.effects.collect { effects += it } }
        runCurrent()
    }

    // ---- banners, permissions ------------------------------------------------------------------

    @Test
    fun `the permission banner lists denied groups and Not now snoozes it`() = test {
        perms.set(Group.FILES, GroupState.DENIED)
        perms.set(Group.CALL_LOG, GroupState.DENIED_PERMANENTLY)
        start()
        runCurrent()
        assertThat(ui.banners.permission?.groups).containsExactly(Group.FILES, Group.CALL_LOG).inOrder()
        vm.onBannerNotNow()
        runCurrent()
        assertThat(ui.banners.permission).isNull()
    }

    @Test
    fun `the indexing banner and the catch-up flag follow the status`() = test {
        start()
        assertThat(ui.banners.indexing).isNull()
        status.mutable.value = IndexStatus(
            phase = IndexPhase.PREPARING,
            mode = Mode.FULL,
            done = 2,
            total = 10,
        )
        runCurrent()
        assertThat(ui.banners.indexing?.done).isEqualTo(2)
    }

    @Test
    fun `banner Allow access requests the first denied group in order`() = test {
        perms.set(Group.CALL_LOG, GroupState.DENIED)
        perms.set(Group.PHOTOS_VIDEOS, GroupState.DENIED)
        start()
        vm.onBannerAllowAccess()
        runCurrent()
        assertThat(effects).containsExactly(HomeEffect.RequestPermission(Group.PHOTOS_VIDEOS))
    }

    @Test
    fun `banner Allow access on a permanently denied group opens the Settings dialog`() = test {
        perms.set(Group.CALL_LOG, GroupState.DENIED_PERMANENTLY)
        start()
        vm.onBannerAllowAccess()
        runCurrent()
        assertThat(effects).isEmpty()
        assertThat(ui.overlay).isEqualTo(Overlay.SettingsDialog(Group.CALL_LOG))
        vm.onSettingsDialogConfirm(Group.CALL_LOG)
        runCurrent()
        assertThat(ui.overlay).isEqualTo(Overlay.None)
        val launch = effects.single() as HomeEffect.Launch
        assertThat(launch.attempts.single().action).isEqualTo("fake.SETTINGS.CALL_LOG")
    }

    @Test
    fun `the Settings dialog is never shown for FILES`() = test {
        perms.set(Group.FILES, GroupState.DENIED_PERMANENTLY)
        start()
        vm.onBannerAllowAccess()
        runCurrent()
        assertThat(ui.overlay).isEqualTo(Overlay.None)
        assertThat(effects).containsExactly(HomeEffect.RequestPermission(Group.FILES))
    }

    @Test
    fun `banner Allow access does nothing when no group is denied`() = test {
        start()
        vm.onBannerAllowAccess()
        runCurrent()
        assertThat(effects).isEmpty()
    }

    @Test
    fun `tab buttons request or open settings directly`() = test {
        perms.set(Group.CALL_LOG, GroupState.DENIED_PERMANENTLY)
        start()
        vm.onTabAllowAccess(Tab.CALLS)
        vm.onTabOpenAppSettings(Tab.CALLS)
        vm.onTabAllowAccess(Tab.ALL)
        runCurrent()
        assertThat(effects).hasSize(2)
        assertThat(effects[0]).isEqualTo(HomeEffect.RequestPermission(Group.CALL_LOG))
        val launch = effects[1] as HomeEffect.Launch
        assertThat(launch.attempts.single().action).isEqualTo("fake.SETTINGS.CALL_LOG")
        assertThat(launch.noHandler).isEqualTo(UiText.Res(R.string.err_settings_open))
        assertThat(ui.overlay).isEqualTo(Overlay.None)
    }

    @Test
    fun `overflow entries open the status screen`() = test {
        start()
        vm.onOverflowStatus()
        vm.onOverflowPermissions()
        vm.onPermissionResult(Group.FILES, GroupState.GRANTED)
        runCurrent()
        assertThat(effects).containsExactly(HomeEffect.OpenStatus(null), HomeEffect.OpenStatus("permissions")).inOrder()
    }

    // ---- rows, sheets and the open guard -------------------------------------------------------

    private val pdf = document(9).copy(contentUri = "content://media/9")

    @Test
    fun `row taps open files messages and sheets`() = test {
        resolver.target = OpenTarget.Ready(android.net.Uri.parse("content://media/9"), "application/pdf", "a.pdf")
        start()
        vm.onRowClick(pdf)
        runCurrent()
        assertThat(effects.single()).isInstanceOf(HomeEffect.Launch::class.java)
        val c = call(2)
        vm.onRowClick(c)
        runCurrent()
        assertThat(ui.overlay).isEqualTo(Overlay.CallSheet(c))
        val r = recording(3)
        vm.onRowClick(r)
        runCurrent()
        assertThat(ui.overlay).isEqualTo(Overlay.RecordingSheet(r))
        vm.onRowMoreClick(pdf)
        runCurrent()
        assertThat(ui.overlay).isEqualTo(Overlay.FileSheet(pdf))
        vm.onRowClick(message(4))
        runCurrent()
        assertThat(effects.last()).isEqualTo(HomeEffect.ShowMessage(UiText.Res(R.string.err_message_open), long = true))
    }

    @Test
    fun `while an open is running further row taps are dropped`() = test {
        resolver.gate = CompletableDeferred()
        resolver.target = OpenTarget.Missing
        start()
        vm.onRowClick(pdf)
        runCurrent()
        vm.onRowClick(pdf)
        vm.onSheetOpen(pdf, chooser = true)
        runCurrent()
        assertThat(resolver.calls).hasSize(1)
        resolver.gate!!.complete(Unit)
        runCurrent()
        assertThat(effects).containsExactly(HomeEffect.ShowMessage(UiText.Res(R.string.err_file_gone), long = true))
        assertThat(control.catchUpNowCalls).isEqualTo(1)
        resolver.gate = null
        vm.onRowClick(pdf)
        runCurrent()
        assertThat(resolver.calls).hasSize(2)
    }

    @Test
    fun `an open without access requests the group or opens the Settings dialog`() = test {
        resolver.target = OpenTarget.NoAccess
        perms.set(Group.PHOTOS_VIDEOS, GroupState.DENIED)
        start()
        val photo = photo(1)
        vm.onRowClick(photo)
        runCurrent()
        assertThat(effects).containsExactly(HomeEffect.RequestPermission(Group.PHOTOS_VIDEOS))
        perms.set(Group.PHOTOS_VIDEOS, GroupState.DENIED_PERMANENTLY)
        vm.onRowClick(photo)
        runCurrent()
        assertThat(ui.overlay).isEqualTo(Overlay.SettingsDialog(Group.PHOTOS_VIDEOS))
    }

    @Test
    fun `sheet actions close the sheet first`() = test {
        resolver.target = OpenTarget.Ready(android.net.Uri.parse("content://media/9"), "application/pdf", "a.pdf")
        start()
        vm.onRowMoreClick(pdf)
        runCurrent()
        vm.onSheetOpen(pdf, chooser = true)
        runCurrent()
        assertThat(ui.overlay).isEqualTo(Overlay.None)
        assertThat(effects.single()).isInstanceOf(HomeEffect.Launch::class.java)
        effects.clear()
        val c = call(2)
        vm.onRowClick(c)
        vm.onSheetCallBack(c)
        vm.onSheetAddContact(c)
        vm.onSheetOpenContact(c)
        runCurrent()
        assertThat(ui.overlay).isEqualTo(Overlay.None)
        assertThat(effects).hasSize(3)
        assertThat(contacts.numbers).containsExactly("+919812345678")
        effects.clear()
        vm.onSheetCallBack(call(5).copy(number = null))
        runCurrent()
        assertThat(effects).isEmpty()
    }

    // ---- player --------------------------------------------------------------------------------

    @Test
    fun `playing a call plays its linked recording and a recording plays itself`() = test {
        start()
        vm.onPlayClick(call(2, linkedRecordingId = 7))
        assertThat(player.played.single().recordingId).isEqualTo(7L)
        assertThat(player.played.single().title).isEqualTo("Rahul")
        vm.onPlayClick(recording(3))
        assertThat(player.played.map { it.recordingId }).containsExactly(7L, 3L).inOrder()
        vm.onPlayClick(call(4))
        assertThat(player.played).hasSize(2)
        assertThat(ui.overlay).isEqualTo(Overlay.None)
    }

    @Test
    fun `playing the current recording toggles it and an errored one retries`() = test {
        start()
        player.set(7L, PlayerPhase.PLAYING)
        vm.onPlayClick(call(2, linkedRecordingId = 7))
        player.set(7L, PlayerPhase.PAUSED)
        vm.onSheetPlay(call(2, linkedRecordingId = 7))
        assertThat(player.toggles).isEqualTo(2)
        player.set(7L, PlayerPhase.ERROR)
        vm.onPlayClick(call(2, linkedRecordingId = 7))
        assertThat(player.retries).isEqualTo(1)
        player.set(8L, PlayerPhase.PLAYING)
        vm.onPlayClick(call(2, linkedRecordingId = 7))
        assertThat(player.played).hasSize(1)
        assertThat(player.toggles).isEqualTo(2)
    }

    @Test
    fun `a failed playback shows the long error message`() = test {
        start()
        player.emit(PlayerEvent.Failed)
        runCurrent()
        assertThat(effects).containsExactly(HomeEffect.ShowMessage(UiText.Res(R.string.err_play), long = true))
    }

    @Test
    fun `rowPlayback tracks the recording and ignores position ticks`() = test {
        start()
        val seen = mutableListOf<RowPlayback>()
        backgroundScope.launch { vm.rowPlayback.collect { seen += it } }
        runCurrent()
        player.set(7L, PlayerPhase.PLAYING)
        runCurrent()
        player.mutable.value = player.mutable.value.copy(positionMs = 1_000)
        player.mutable.value = player.mutable.value.copy(positionMs = 2_000)
        runCurrent()
        player.set(7L, PlayerPhase.PAUSED)
        runCurrent()
        player.set(null, PlayerPhase.CLOSED)
        runCurrent()
        assertThat(seen).containsExactly(
            RowPlayback(null, false),
            RowPlayback(7L, true),
            RowPlayback(7L, false),
            RowPlayback(null, false),
        ).inOrder()
    }

    @Test
    fun `pausePlayback pauses the player and onCleared closes it`() = test {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = PreferenceDataStoreFactory.create(scope = storeScope) {
            File(tmp.root, "sidx_app.preferences_pb")
        }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(
                index, status, perms, envSource, HomePrefs(dataStore, HomeClock { now }), actions, player,
                HomeClock { now }, context, dispatcher, SavedStateHandle(),
            ) as T
        }
        val model = ViewModelProvider.create(store, factory)[HomeViewModel::class.java]
        model.pausePlayback()
        assertThat(player.pauses).isEqualTo(1)
        assertThat(player.closes).isEqualTo(0)
        store.clear()
        assertThat(player.closes).isEqualTo(1)
    }

    // ---- logging -------------------------------------------------------------------------------

    @Test
    fun `no log line contains the query or the item data`() = test {
        resolver.target = OpenTarget.Missing
        start()
        vm.onSearchBarClick()
        type("secretquery")
        settle()
        vm.onRowClick(document(9).copy(title = "TopSecretTitle.pdf"))
        vm.onPlayClick(call(2, linkedRecordingId = 7))
        runCurrent()
        assertThat(logged).isNotEmpty()
        logged.forEach {
            assertThat(it).doesNotContain("secretquery")
            assertThat(it).doesNotContain("TopSecretTitle")
            assertThat(it).doesNotContain("Rahul")
            assertThat(it).doesNotContain("919812345678")
        }
    }

    @Test
    fun `uiState commit ids are strictly increasing`() = test {
        start()
        vm.onPrevDay()
        runCurrent()
        vm.onSearchBarClick()
        type("ra")
        settle()
        val ids = states.map { it.commit.id }.distinctUntilChangedList()
        assertThat(ids).isInStrictOrder()
        assertThat(ids.size).isAtLeast(3)
        assertThat(states.map { it.commit.id }.distinct()).hasSize(ids.size)
    }

    private fun <T> List<T>.distinctUntilChangedList(): List<T> =
        fold(mutableListOf()) { acc, v ->
            if (acc.lastOrNull() != v) acc.add(v)
            acc
        }

    private companion object {
        const val DEBOUNCE = 300L
    }
}
