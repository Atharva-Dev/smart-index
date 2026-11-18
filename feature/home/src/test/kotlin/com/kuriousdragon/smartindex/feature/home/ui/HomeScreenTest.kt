package com.kuriousdragon.smartindex.feature.home.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.paging.LoadState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.designsystem.theme.SmartIndexTheme
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.IndexPhase
import com.kuriousdragon.smartindex.core.model.IndexStatus
import com.kuriousdragon.smartindex.core.model.ItemDetail
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.core.model.OpenTarget
import com.kuriousdragon.smartindex.core.model.PerfTrace
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceKey
import com.kuriousdragon.smartindex.feature.home.HomeRoute
import com.kuriousdragon.smartindex.feature.home.HomeTrace
import com.kuriousdragon.smartindex.feature.home.model.DateFilter
import com.kuriousdragon.smartindex.feature.home.model.Tab
import com.kuriousdragon.smartindex.feature.home.state.HomeMode
import com.kuriousdragon.smartindex.feature.home.testing.FakeIndexQuery
import com.kuriousdragon.smartindex.feature.home.testing.HomeHarness
import com.kuriousdragon.smartindex.feature.home.testing.TestEnv
import com.kuriousdragon.smartindex.feature.home.testing.call
import com.kuriousdragon.smartindex.feature.home.testing.document
import com.kuriousdragon.smartindex.feature.home.testing.londonTime
import com.kuriousdragon.smartindex.feature.home.testing.message
import com.kuriousdragon.smartindex.feature.home.testing.photo
import com.kuriousdragon.smartindex.feature.home.testing.recording
import com.kuriousdragon.smartindex.feature.home.testing.video
import com.kuriousdragon.smartindex.feature.player.PlayerEvent
import com.kuriousdragon.smartindex.feature.player.PlayerPhase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w1000dp-h900dp")
class HomeScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var h: HomeHarness
    private val opened = mutableListOf<String?>()
    private val begins = mutableListOf<Pair<String, Int>>()
    private val ends = mutableListOf<Pair<String, Int>>()
    private val oldSink = HomeTrace.sink
    private val today = TestEnv.today
    private val morning = londonTime("2026-11-03", "09:15")
    private val afternoon = londonTime("2026-11-03", "14:00")
    private val old = londonTime("2026-10-29", "10:00")

    @Before
    fun setUp() {
        h = HomeHarness(tmp.newFolder())
        HomeTrace.sink = object : HomeTrace.Sink {
            override fun begin(name: String, cookie: Int) {
                begins += name to cookie
            }

            override fun end(name: String, cookie: Int) {
                ends += name to cookie
            }
        }
        h.index.timelineItems = { day, type ->
            val all = when (day) {
                today -> listOf(photo(1, morning), document(2, afternoon), call(3, afternoon), video(4, morning))
                else -> emptyList()
            }
            all.filter { type == null || it.type == type }.sortedBy { it.time }
        }
        h.index.dayCounts = { day ->
            if (day == today) {
                FakeIndexQuery.zeroCounts() + mapOf(
                    ItemType.PHOTO to 1,
                    ItemType.DOCUMENT to 1,
                    ItemType.CALL to 1,
                    ItemType.VIDEO to 1,
                )
            } else {
                FakeIndexQuery.zeroCounts()
            }
        }
        h.index.searchItems = { listOf(message(11, morning), photo(12, old)) }
    }

    @After
    fun tearDown() {
        HomeTrace.sink = oldSink
        h.close()
    }

    private fun show() {
        rule.setContent {
            SmartIndexTheme(darkTheme = false) {
                HomeRoute(
                    onOpenStatus = { opened += it },
                    viewModel = h.viewModel,
                    launcher = h.launcher,
                    isChangingConfigurations = { h.changingConfigurations },
                )
            }
        }
        settle()
    }

    private fun settle(ms: Long = 0) {
        h.advance(ms)
        rule.waitForIdle()
    }

    private fun tag(tag: String) = rule.onNodeWithTag(tag)

    private fun rows() = rule.onAllNodesWithTag(HomeTestTags.ROW)

    private fun enterSearchAndType(text: String) {
        tag(HomeTestTags.SEARCH_BAR).performClick()
        settle()
        tag(HomeTestTags.SEARCH_FIELD).performTextInput(text)
        settle(301)
    }

    private fun pressBack() {
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    // ---- first composition, Search, back, tabs ----

    @Test
    fun `first composition shows today's timeline rows under part-of-day headers`() {
        show()
        rows().assertCountEquals(4)
        rule.onNodeWithText("Morning").assertIsDisplayed()
        rule.onNodeWithText("Afternoon").assertIsDisplayed()
        assertThat(h.index.timelineCalls.first()).isEqualTo(today to null)
    }

    @Test
    fun `tapping the search bar shows the field and the hint`() {
        show()
        tag(HomeTestTags.SEARCH_BAR).performClick()
        settle()
        tag(HomeTestTags.SEARCH_FIELD).assertIsDisplayed()
        tag(HomeTestTags.HINT).assertIsDisplayed()
        rule.onAllNodesWithTag(HomeTestTags.SEARCH_BAR).assertCountEquals(0)
    }

    @Test
    fun `typing shows results after 300 ms with date headers`() {
        show()
        tag(HomeTestTags.SEARCH_BAR).performClick()
        settle()
        tag(HomeTestTags.SEARCH_FIELD).performTextInput("ra")
        settle(299)
        tag(HomeTestTags.HINT).assertExists()
        assertThat(h.index.searchCalls).isEmpty()
        settle(2)
        rows().assertCountEquals(2)
        rule.onNodeWithText("Today · Tue, 3 Nov").assertExists()
        rule.onNodeWithText("Thu, 29 Oct 2026").assertExists()
        assertThat(h.index.searchCalls.single().text).isEqualTo("ra")
    }

    @Test
    fun `the clear button empties the field and returns to the hint`() {
        show()
        enterSearchAndType("ra")
        rows().assertCountEquals(2)
        tag(HomeTestTags.SEARCH_CLEAR).performClick()
        settle()
        assertThat(h.viewModel.uiState.value.query).isEmpty()
        tag(HomeTestTags.HINT).assertExists()
        rule.onAllNodesWithTag(HomeTestTags.SEARCH_CLEAR).assertCountEquals(0)
    }

    @Test
    fun `back leaves Search to the previous day`() {
        show()
        tag(HomeTestTags.PREV_DAY).performClick()
        settle()
        tag(HomeTestTags.SEARCH_BAR).performClick()
        settle()
        assertThat(h.viewModel.uiState.value.mode).isEqualTo(HomeMode.SEARCH)
        pressBack()
        assertThat(h.viewModel.uiState.value.mode).isEqualTo(HomeMode.HOME)
        assertThat(h.viewModel.uiState.value.day).isEqualTo(today.minusDays(1))
        tag(HomeTestTags.SEARCH_BAR).assertIsDisplayed()
        rule.onNodeWithText("Yesterday · Mon, 2 Nov", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the eight tabs switch the list`() {
        show()
        val expected = mapOf(
            Tab.ALL to 4,
            Tab.PHOTOS to 1,
            Tab.VIDEOS to 1,
            Tab.DOCUMENTS to 1,
            Tab.CALLS to 1,
        )
        for ((tab, count) in expected) {
            tag(tab.tag).performClick()
            settle()
            rows().assertCountEquals(count)
            assertThat(h.index.timelineCalls.last()).isEqualTo(today to tab.type)
        }
        for (tab in Tab.entries) tag(tab.tag).assertExists()
    }

    @Test
    fun `switching tabs in Search keeps the keyword and the date`() {
        show()
        enterSearchAndType("ra")
        tag(HomeTestTags.DATE_CHIP).performClick()
        settle()
        tag(HomeTestTags.DATE_PRESET_TODAY).performClick()
        settle(301)
        tag(Tab.CALLS.tag).performClick()
        settle(301)
        val last = h.index.searchCalls.last()
        assertThat(last.text).isEqualTo("ra")
        assertThat(last.range).isNotNull()
        assertThat(last.type).isEqualTo(ItemType.CALL)
    }

    @Test
    fun `a locked tab shows the permission view and granting the group shows the list`() {
        h.perms.set(Group.CALL_LOG, GroupState.DENIED)
        show()
        tag(Tab.CALLS.tag).performClick()
        settle()
        tag(HomeTestTags.TAB_LOCKED).assertIsDisplayed()
        rows().assertCountEquals(0)
        h.perms.set(Group.CALL_LOG, GroupState.GRANTED)
        settle()
        rule.onAllNodesWithTag(HomeTestTags.TAB_LOCKED).assertCountEquals(0)
        rows().assertCountEquals(1)
    }

    // ---- list statuses ----

    @Test
    fun `an empty day shows the empty day state`() {
        h.index.timelineItems = { _, _ -> emptyList() }
        show()
        tag(HomeTestTags.EMPTY_DAY).assertIsDisplayed()
    }

    @Test
    fun `an empty tab on a non-empty day shows the empty tab state`() {
        h.index.dayCounts = { FakeIndexQuery.zeroCounts() + (ItemType.PHOTO to 1) }
        show()
        tag(Tab.RECORDINGS.tag).performClick()
        settle()
        tag(HomeTestTags.EMPTY_TAB).assertIsDisplayed()
        rule.onNodeWithText("Show all types").performClick()
        settle()
        assertThat(h.viewModel.uiState.value.tab).isEqualTo(Tab.ALL)
    }

    @Test
    fun `a search without matches shows no results`() {
        h.index.searchItems = { emptyList() }
        show()
        enterSearchAndType("zzz")
        tag(HomeTestTags.NO_RESULTS).assertIsDisplayed()
    }

    @Test
    fun `a date-only search without items shows the empty range state`() {
        h.index.searchItems = { emptyList() }
        show()
        tag(HomeTestTags.SEARCH_BAR).performClick()
        settle()
        tag(HomeTestTags.DATE_CHIP).performClick()
        settle()
        tag(HomeTestTags.DATE_PRESET_LAST_7_DAYS).performClick()
        settle(301)
        tag(HomeTestTags.EMPTY_RANGE).assertIsDisplayed()
    }

    @Test
    fun `the skeleton appears only after 300 ms of loading and the first page replaces it`() {
        val gate = CompletableDeferred<Unit>()
        h.index.gate = gate
        rule.mainClock.autoAdvance = false
        show()
        rule.onAllNodesWithTag(HomeTestTags.SKELETON).assertCountEquals(0)
        rule.mainClock.advanceTimeBy(299)
        rule.onAllNodesWithTag(HomeTestTags.SKELETON).assertCountEquals(0)
        rule.mainClock.advanceTimeBy(2)
        rule.onAllNodesWithTag(HomeTestTags.SKELETON).onFirst().assertExists()
        rule.mainClock.autoAdvance = true
        gate.complete(Unit)
        settle()
        rule.onAllNodesWithTag(HomeTestTags.SKELETON).assertCountEquals(0)
        rows().assertCountEquals(4)
    }

    @Test
    fun `a slow short query shows the skeleton and progress, then rows, and an empty page shows no results`() {
        show()
        val firstPage = CompletableDeferred<Unit>()
        h.index.gate = firstPage
        h.index.searchItems = { listOf(message(11, morning)) }
        enterSearchAndType("q")
        rule.onAllNodesWithTag(HomeTestTags.SKELETON).assertCountEquals(0)
        rule.mainClock.advanceTimeBy(301)
        rule.waitForIdle()
        rule.onAllNodesWithTag(HomeTestTags.SKELETON).onFirst().assertExists()
        rule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
        ).onFirst().assertExists()
        rule.onAllNodesWithTag(HomeTestTags.NO_RESULTS).assertCountEquals(0)
        rows().assertCountEquals(0)

        firstPage.complete(Unit)
        settle()
        rows().assertCountEquals(1)
        rule.onAllNodesWithTag(HomeTestTags.SKELETON).assertCountEquals(0)

        val emptyPage = CompletableDeferred<Unit>()
        h.index.gate = emptyPage
        h.index.searchItems = { emptyList() }
        tag(HomeTestTags.SEARCH_FIELD).performTextInput("z")
        settle(301)
        rule.mainClock.advanceTimeBy(301)
        rule.waitForIdle()
        rule.onAllNodesWithTag(HomeTestTags.SKELETON).onFirst().assertExists()
        rule.onAllNodesWithTag(HomeTestTags.NO_RESULTS).assertCountEquals(0)
        emptyPage.complete(Unit)
        settle()
        tag(HomeTestTags.NO_RESULTS).assertIsDisplayed()
        rule.onAllNodesWithTag(HomeTestTags.SKELETON).assertCountEquals(0)
    }

    @Test
    fun `a load error shows the error view with Try again`() {
        h.index.loadStates = FakeIndexQuery.SETTLED.copy(
            refresh = LoadState.Error(IllegalStateException("boom")),
        )
        show()
        tag(HomeTestTags.LOAD_ERROR).assertIsDisplayed()
        rule.onNodeWithText("Try again").assertIsDisplayed().performClick()
        settle()
        tag(HomeTestTags.LOAD_ERROR).assertExists()
    }

    @Test
    fun `an append error shows a footer with Try again under the rows`() {
        h.index.loadStates = FakeIndexQuery.SETTLED.copy(
            append = LoadState.Error(IllegalStateException("boom")),
        )
        show()
        rows().assertCountEquals(4)
        rule.onNodeWithText("Try again").assertExists()
        rule.onAllNodesWithTag(HomeTestTags.LOAD_ERROR).assertCountEquals(0)
    }

    // ---- banners ----

    @Test
    fun `the indexing banner shows above the rows`() {
        h.status.mutable.value = IndexStatus(phase = IndexPhase.PREPARING, mode = Mode.FULL, done = 10, total = 100)
        show()
        tag(HomeTestTags.INDEXING_BANNER).assertIsDisplayed()
        val banner = tag(HomeTestTags.INDEXING_BANNER).getUnclippedBoundsInRoot()
        val firstRow = rows().onFirst().getUnclippedBoundsInRoot()
        assertThat(banner.bottom.value).isAtMost(firstRow.top.value)
    }

    @Test
    fun `the permission banner snoozes with Not now`() {
        h.perms.set(Group.CALL_LOG, GroupState.DENIED)
        show()
        tag(HomeTestTags.PERMISSION_BANNER).assertIsDisplayed()
        rule.onNodeWithText("Not now").performClick()
        settle()
        rule.onAllNodesWithTag(HomeTestTags.PERMISSION_BANNER).assertCountEquals(0)
        rows().assertCountEquals(4)
    }

    // ---- sheets and pickers ----

    @Test
    fun `the more button opens the file sheet and Open resolves the file`() {
        h.resolver.target = OpenTarget.Ready(android.net.Uri.parse("content://x/1"), "image/jpeg", "IMG_1.jpg")
        show()
        rule.onAllNodesWithContentDescription("More actions").onFirst().performClick()
        settle()
        tag(HomeTestTags.SHEET_FILE).assertIsDisplayed()
        rule.onNodeWithText("Open").performClick()
        settle()
        rule.onAllNodesWithTag(HomeTestTags.SHEET_FILE).assertCountEquals(0)
        assertThat(h.resolver.calls).hasSize(1)
        assertThat(h.launched).hasSize(1)
    }

    @Test
    fun `tapping a call opens the call sheet and recordings open the recording sheet`() {
        h.index.timelineItems = { _, _ -> listOf(call(3, afternoon, linkedRecordingId = 5), recording(5, morning)) }
        show()
        rows().onFirst().performClick()
        settle()
        tag(HomeTestTags.SHEET_CALL).assertIsDisplayed()
        rule.onNodeWithText("Play recording").performClick()
        settle()
        assertThat(h.player.played.single().recordingId).isEqualTo(5)
        rows()[1].performClick()
        settle()
        tag(HomeTestTags.SHEET_RECORDING).assertIsDisplayed()
    }

    @Test
    fun `the date button opens the day picker and the chip opens the date sheet`() {
        show()
        tag(HomeTestTags.DATE_BUTTON).performClick()
        settle()
        rule.onNodeWithText("Go to date").assertIsDisplayed()
        rule.onNodeWithText("Cancel").performClick()
        settle()
        tag(HomeTestTags.SEARCH_BAR).performClick()
        settle()
        tag(HomeTestTags.DATE_CHIP).performClick()
        settle()
        rule.onNodeWithText("Filter by date").assertIsDisplayed()
        tag(HomeTestTags.DATE_PRESET_YESTERDAY).performClick()
        settle(301)
        assertThat(h.viewModel.uiState.value.dateFilter)
            .isEqualTo(DateFilter.Yesterday)
    }

    @Test
    fun `the custom range picker opens from the date sheet`() {
        show()
        tag(HomeTestTags.SEARCH_BAR).performClick()
        settle()
        tag(HomeTestTags.DATE_CHIP).performClick()
        settle()
        rule.onNodeWithText("Custom range…").performClick()
        settle()
        rule.onNodeWithText("Custom range").assertIsDisplayed()
    }

    // ---- effects ----

    @Test
    fun `Launch tries the attempts in order`() {
        h.index.details[7] = detail(7, threadId = 42, numberRaw = "+919812345678")
        h.unresolvable = { it.data?.scheme == "content" }
        h.index.searchItems = { listOf(message(7, morning)) }
        show()
        enterSearchAndType("see")
        rows().onFirst().performClick()
        settle()
        assertThat(h.launched.map { it.data?.scheme }).containsExactly("content", "sms").inOrder()
        rule.onAllNodesWithText("No app on this phone", substring = true).assertCountEquals(0)
    }

    @Test
    fun `Launch shows the no-app snackbar when every attempt fails`() {
        h.index.details[7] = detail(7, threadId = 42, numberRaw = "+919812345678")
        h.unresolvable = { true }
        h.index.searchItems = { listOf(message(7, morning)) }
        show()
        enterSearchAndType("see")
        rows().onFirst().performClick()
        settle()
        assertThat(h.launched).hasSize(2)
        rule.onNodeWithText("No app on this phone can open this message.").assertIsDisplayed()
    }

    @Test
    fun `a long error snackbar has a Dismiss action that removes it at once`() {
        show()
        h.player.emit(PlayerEvent.Failed)
        settle()
        rule.onNodeWithText("Can't play this recording", substring = true).assertExists()
        rule.onAllNodesWithContentDescription("Dismiss").assertCountEquals(1)
        rule.onNodeWithContentDescription("Dismiss").performClick()
        settle()
        rule.onAllNodesWithText("Can't play this recording", substring = true).assertCountEquals(0)
    }

    @Test
    fun `a short message snackbar has no Dismiss action`() {
        h.index.details[7] = detail(7, threadId = 42, numberRaw = "+919812345678")
        h.unresolvable = { true }
        h.index.searchItems = { listOf(message(7, morning)) }
        show()
        enterSearchAndType("see")
        rows().onFirst().performClick()
        settle()
        rule.onNodeWithText("No app on this phone can open this message.").assertIsDisplayed()
        rule.onAllNodesWithContentDescription("Dismiss").assertCountEquals(0)
    }

    @Test
    fun `an error message stays for 10 seconds`() {
        show()
        h.player.emit(PlayerEvent.Failed)
        settle()
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Can't play this recording", substring = true).assertExists()
        rule.mainClock.advanceTimeBy(6_000)
        rule.onNodeWithText("Can't play this recording", substring = true).assertExists()
        rule.mainClock.advanceTimeBy(5_000)
        rule.waitForIdle()
        rule.onAllNodesWithText("Can't play this recording", substring = true).assertCountEquals(0)
    }

    @Test
    fun `Index status and Permissions open the status screen`() {
        show()
        h.viewModel.onOverflowStatus()
        h.viewModel.onOverflowPermissions()
        settle()
        assertThat(opened).containsExactly(null, "permissions").inOrder()
    }

    // ---- tags, drawn, lifecycle, mini-player ----

    @Test
    fun `testTagsAsResourceId is set and the Home tags of the LLD are found`() {
        h.status.mutable.value = IndexStatus(phase = IndexPhase.PREPARING, mode = Mode.FULL, done = 1, total = 10)
        show()
        rule.onAllNodes(
            SemanticsMatcher("testTagsAsResourceId") { node ->
                node.config.any { (key, value) -> key.name == "TestTagsAsResourceId" && value == true }
            },
        ).onFirst().assertExists()
        for (t in listOf(
            HomeTestTags.SEARCH_BAR,
            HomeTestTags.LIST,
            HomeTestTags.PREV_DAY,
            HomeTestTags.NEXT_DAY,
            HomeTestTags.DATE_BUTTON,
            HomeTestTags.INDEXING_BANNER,
        )) {
            tag(t).assertExists()
        }
        rows().onFirst().assertExists()
        for (tab in Tab.entries) tag(tab.tag).assertExists()
        tag(HomeTestTags.SEARCH_BAR).performClick()
        settle()
        tag(HomeTestTags.SEARCH_FIELD).assertExists()
        tag(HomeTestTags.DATE_CHIP).performClick()
        settle()
        for (preset in listOf(
            HomeTestTags.DATE_PRESET_ANY,
            HomeTestTags.DATE_PRESET_TODAY,
            HomeTestTags.DATE_PRESET_YESTERDAY,
            HomeTestTags.DATE_PRESET_LAST_7_DAYS,
            HomeTestTags.DATE_PRESET_LAST_30_DAYS,
        )) {
            tag(preset).assertExists()
        }
    }

    @Test
    fun `the drawn signal flips after the first page and the trace ends once`() {
        // ReportDrawnWhen { state.firstPageShown }: Robolectric never draws the decor view, so the activity's
        // FullyDrawnReporter cannot report here; the condition it reads is asserted instead.
        val gate = CompletableDeferred<Unit>()
        h.index.gate = gate
        show()
        assertThat(h.viewModel.uiState.value.firstPageShown).isFalse()
        assertThat(ends).isEmpty()
        assertThat(begins.map { it.first }).containsExactly(PerfTrace.TIMELINE_FIRST_PAGE)
        gate.complete(Unit)
        settle()
        assertThat(h.viewModel.uiState.value.firstPageShown).isTrue()
        assertThat(ends).containsExactly(begins.single())
    }

    @Test
    fun `stopping the activity pauses playback unless it is changing configuration`() {
        show()
        h.changingConfigurations = true
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.waitForIdle()
        assertThat(h.player.pauses).isEqualTo(0)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
        h.changingConfigurations = false
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.waitForIdle()
        assertThat(h.player.pauses).isEqualTo(1)
    }

    @Test
    fun `the mini-player docks above the snackbar and the list keeps its bottom padding`() {
        h.index.timelineItems = { day, _ -> if (day == today) (1L..30L).map { photo(it, morning) } else emptyList() }
        show()
        h.player.set(9, PlayerPhase.PLAYING)
        h.player.emit(PlayerEvent.Failed)
        settle()
        val miniPlayer = tag(HomeTestTags.MINI_PLAYER).getUnclippedBoundsInRoot()
        val snackbar = rule.onNodeWithText("Can't play this recording", substring = true).getUnclippedBoundsInRoot()
        assertThat(snackbar.bottom.value).isAtMost(miniPlayer.top.value)

        tag(HomeTestTags.LIST).performScrollToIndex(30)
        settle()
        assertThat(tag(HomeTestTags.LIST).getUnclippedBoundsInRoot().bottom.value).isAtMost(miniPlayer.top.value)
        val lastRow = rows().onLast().getUnclippedBoundsInRoot()
        assertThat(lastRow.bottom.value).isAtMost(miniPlayer.top.value)
    }

    private fun detail(id: Long, threadId: Long?, numberRaw: String?) = ItemDetail(
        id = id, key = SourceKey(Source.SMS, id), type = ItemType.MESSAGE, title = "Rahul", primaryTs = morning,
        dates = emptyMap(), folder = null, mime = null, ext = null, sizeBytes = null, durationMs = null,
        contentUri = null, numberRaw = numberRaw, contactName = "Rahul", direction = null, msgKind = null,
        threadId = threadId, hasAttachment = false, body = "See you at the hotel", linkedRecordingId = null,
        linkedCallId = null,
    )
}
