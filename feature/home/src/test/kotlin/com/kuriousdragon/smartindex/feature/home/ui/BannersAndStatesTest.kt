package com.kuriousdragon.smartindex.feature.home.ui

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.designsystem.theme.SmartIndexTheme
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.feature.home.model.Tab
import com.kuriousdragon.smartindex.feature.home.state.EmptyKind
import com.kuriousdragon.smartindex.feature.home.state.ErrorKind
import com.kuriousdragon.smartindex.feature.home.state.IndexingBanner
import com.kuriousdragon.smartindex.feature.home.state.PermissionBanner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class BannersAndStatesTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(fontScale: Float = 1f, modifier: Modifier = Modifier, content: @Composable (Modifier) -> Unit) {
        rule.setContent {
            SmartIndexTheme(darkTheme = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    content(modifier)
                }
            }
        }
    }

    private fun description(tag: String): List<String>? =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)

    private fun assertText(text: String) = rule.onNode(hasText(text, substring = true)).assertIsDisplayed()

    // --- Indexing banner ---

    @Test
    fun `indexing banner shows grouped numbers and determinate progress`() {
        show { IndexingBanner(IndexingBanner(done = 1234, total = 56789), it) }

        rule.onNodeWithTag(HomeTestTags.INDEXING_BANNER).assertIsDisplayed()
        assertText("Indexing your phone… 1,234 of 56,789 items")
        assertText("Results appear as they are found")
        val progress = rule.onNode(
            androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
        ).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertThat(progress).isNotEqualTo(ProgressBarRangeInfo.Indeterminate)
        assertThat(progress.current).isWithin(0.001f).of(1234f / 56789f)
    }

    @Test
    fun `indexing banner with unknown total shows the short title and indeterminate progress`() {
        show { IndexingBanner(IndexingBanner(done = 40, total = 0), it) }

        assertThat(description(HomeTestTags.INDEXING_BANNER)).containsExactly("Indexing your phone…")
        val progress = rule.onNode(
            androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
        ).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertThat(progress).isEqualTo(ProgressBarRangeInfo.Indeterminate)
    }

    @Test
    fun `indexing banner is a polite live region announcing the percent rounded down to 10`() {
        show { IndexingBanner(IndexingBanner(done = 379, total = 1000), it) }

        val config = rule.onNodeWithTag(HomeTestTags.INDEXING_BANNER).fetchSemanticsNode().config
        assertThat(config[SemanticsProperties.LiveRegion]).isEqualTo(LiveRegionMode.Polite)
        assertThat(description(HomeTestTags.INDEXING_BANNER))
            .containsExactly("Indexing your phone… 379 of 1,000 items 30 percent")
    }

    @Test
    fun `announced percent rounds down to a multiple of 10`() {
        assertThat(announcedPercent(0, 100)).isEqualTo(0)
        assertThat(announcedPercent(9, 100)).isEqualTo(0)
        assertThat(announcedPercent(10, 100)).isEqualTo(10)
        assertThat(announcedPercent(99, 100)).isEqualTo(90)
        assertThat(announcedPercent(100, 100)).isEqualTo(100)
        assertThat(announcedPercent(5, 0)).isEqualTo(0)
        assertThat(announcedPercent(500, 100)).isEqualTo(100)
    }

    // --- Permission banner ---

    @Test
    fun `permission banner renders title body and fires both actions`() {
        var notNow = 0
        var allow = 0
        show {
            PermissionBanner(
                PermissionBanner(listOf(Group.CALL_LOG, Group.MESSAGES)),
                onNotNow = { notNow++ },
                onAllow = { allow++ },
                modifier = it,
            )
        }

        rule.onNodeWithTag(HomeTestTags.PERMISSION_BANNER).assertIsDisplayed()
        assertText("Calls and messages are hidden")
        assertText("Smart Index can't read your call history and your messages yet. Allow access to include them.")
        rule.onNodeWithText("Not now").performClick()
        rule.onNodeWithText("Allow access").performClick()
        assertThat(notNow).isEqualTo(1)
        assertThat(allow).isEqualTo(1)
        val config = rule.onNodeWithTag(HomeTestTags.PERMISSION_BANNER).fetchSemanticsNode().config
        assertThat(config[SemanticsProperties.LiveRegion]).isEqualTo(LiveRegionMode.Polite)
    }

    @Test
    fun `permission banner copy for three and one groups`() {
        show {
            PermissionBanner(
                PermissionBanner(listOf(Group.PHOTOS_VIDEOS, Group.FILES, Group.CALL_LOG)),
                onNotNow = {},
                onAllow = {},
                modifier = it,
            )
        }
        assertText("Photos and videos, files and recordings and calls are hidden")
    }

    // --- Hint ---

    @Test
    fun `hint view renders its copy and tag`() {
        show { HintView(it) }

        rule.onNodeWithTag(HomeTestTags.HINT).assertIsDisplayed()
        assertText("Search everything on this phone")
        assertText("Type a word from a message")
        assertText("Examples: \"Rahul\", \"hotel\", \"invoice\"")
    }

    // --- Empty states ---

    @Test
    fun `empty day fills the date and fires pick another date`() {
        var picked = 0
        show { EmptyView(EmptyKind.EMPTY_DAY, it, dayLabel = "Mon, 3 Nov", onPickDate = { picked++ }) }

        rule.onNodeWithTag(HomeTestTags.EMPTY_DAY).assertIsDisplayed()
        assertText("Nothing on this day")
        assertText("No photos, videos, files, calls or messages are dated Mon, 3 Nov.")
        rule.onNodeWithText("Pick another date").performClick()
        assertThat(picked).isEqualTo(1)
    }

    @Test
    fun `empty tab names the type and fires show all types`() {
        var shown = 0
        show {
            EmptyView(EmptyKind.EMPTY_TAB, it, dayLabel = "Mon, 3 Nov", tab = Tab.OTHER_FILES, onShowAllTypes = {
                shown++
            })
        }

        rule.onNodeWithTag(HomeTestTags.EMPTY_TAB).assertIsDisplayed()
        assertText("No other files on this day")
        assertText("Nothing of this type is dated Mon, 3 Nov. Try another type or date.")
        rule.onNodeWithText("Show all types").performClick()
        assertThat(shown).isEqualTo(1)
    }

    @Test
    fun `no results on All without a date filter has no actions`() {
        show { EmptyView(EmptyKind.NO_RESULTS, it, query = "rahul", tab = Tab.ALL) }

        rule.onNodeWithTag(HomeTestTags.NO_RESULTS).assertIsDisplayed()
        assertText("No results for \"rahul\"")
        assertText("Check the spelling or try fewer words.")
        rule.onNodeWithText("Clear date filter").assertDoesNotExist()
        rule.onNodeWithText("Search all types").assertDoesNotExist()
        rule.onNode(hasText("The date filter", substring = true)).assertDoesNotExist()
    }

    @Test
    fun `no results with a date filter and a type tab shows the note and both actions`() {
        var cleared = 0
        var all = 0
        show {
            EmptyView(
                EmptyKind.NO_RESULTS,
                it,
                query = "rahul",
                tab = Tab.PHOTOS,
                dateFilterLabel = "Last 7 days",
                onClearDateFilter = { cleared++ },
                onSearchAllTypes = { all++ },
            )
        }

        assertText("The date filter Last 7 days is on.")
        rule.onNodeWithText("Clear date filter").performClick()
        rule.onNodeWithText("Search all types").performClick()
        assertThat(cleared).isEqualTo(1)
        assertThat(all).isEqualTo(1)
    }

    @Test
    fun `no results clear date filter only with a filter and search all types only off All`() {
        show { EmptyView(EmptyKind.NO_RESULTS, it, query = "x", tab = Tab.CALLS, dateFilterLabel = null) }
        rule.onNodeWithText("Clear date filter").assertDoesNotExist()
        rule.onNodeWithText("Search all types").assertIsDisplayed()
    }

    @Test
    fun `no results with a filter on the All tab shows only clear date filter`() {
        show { EmptyView(EmptyKind.NO_RESULTS, it, query = "x", tab = Tab.ALL, dateFilterLabel = "Today") }
        rule.onNodeWithText("Clear date filter").assertIsDisplayed()
        rule.onNodeWithText("Search all types").assertDoesNotExist()
    }

    @Test
    fun `empty range fills the range and clears the date filter`() {
        var cleared = 0
        show {
            EmptyView(EmptyKind.EMPTY_RANGE, it, dayLabel = "1 Oct – 5 Oct", onClearDateFilter = { cleared++ })
        }

        rule.onNodeWithTag(HomeTestTags.EMPTY_RANGE).assertIsDisplayed()
        assertText("Nothing in this date range")
        assertText("are dated 1 Oct – 5 Oct.")
        rule.onNodeWithText("Clear date filter").performClick()
        assertThat(cleared).isEqualTo(1)
    }

    // --- Tab locked ---

    @Test
    fun `tab locked copy per tab`() {
        val cases = listOf(
            Triple(Tab.PHOTOS, Group.PHOTOS_VIDEOS, "Allow access to photos and videos") to
                "Smart Index needs photo and video access to search your photos and videos. Photos and videos are read on this phone only and never leave it.",
            Triple(Tab.DOCUMENTS, Group.FILES, "Allow access to files") to
                "Smart Index needs All files access to search your documents. Documents are read on this phone only and never leave it.",
            Triple(Tab.RECORDINGS, Group.FILES, "Allow access to call recordings") to
                "Smart Index needs All files access to search your call recordings. Call recordings are read on this phone only and never leave it.",
            Triple(Tab.CALLS, Group.CALL_LOG, "Allow access to calls") to
                "Smart Index needs the call history permission to search your calls. Calls are read on this phone only and never leave it.",
            Triple(Tab.MESSAGES, Group.MESSAGES, "Allow access to messages") to
                "Smart Index needs the SMS permission to search your messages. Messages are read on this phone only and never leave it.",
        )
        var current by mutableStateOf(cases.first().first.let { it.first to it.second })
        rule.setContent {
            SmartIndexTheme(darkTheme = false) {
                TabLockedView(current.first, current.second, GroupState.DENIED, onAllow = {}, onOpenSettings = {})
            }
        }
        cases.forEach { (key, body) ->
            current = key.first to key.second
            rule.waitForIdle()
            rule.onNodeWithTag(HomeTestTags.TAB_LOCKED).assertIsDisplayed()
            assertText(key.third)
            assertText(body)
        }
    }

    @Test
    fun `tab locked denied shows allow access only`() {
        var allow = 0
        var settings = 0
        show {
            TabLockedView(Tab.CALLS, Group.CALL_LOG, GroupState.DENIED, { allow++ }, { settings++ }, it)
        }

        rule.onNodeWithText("Open app settings").assertDoesNotExist()
        rule.onNodeWithText("Allow access").performClick()
        assertThat(allow).isEqualTo(1)
        assertThat(settings).isEqualTo(0)
    }

    @Test
    fun `tab locked permanently denied shows open app settings only`() {
        var allow = 0
        var settings = 0
        show {
            TabLockedView(
                Tab.MESSAGES,
                Group.MESSAGES,
                GroupState.DENIED_PERMANENTLY,
                { allow++ },
                { settings++ },
                it,
            )
        }

        rule.onNodeWithText("Allow access").assertDoesNotExist()
        rule.onNodeWithText("Open app settings").performClick()
        assertThat(settings).isEqualTo(1)
        assertThat(allow).isEqualTo(0)
    }

    // --- Errors ---

    @Test
    fun `error view per kind fires retry`() {
        var retried = 0
        show { ErrorView(ErrorKind.DAY, { retried++ }, it) }

        rule.onNodeWithTag(HomeTestTags.LOAD_ERROR).assertIsDisplayed()
        assertText("Couldn't load this day")
        assertText("Something went wrong while reading the index.")
        rule.onNodeWithText("Try again").performClick()
        assertThat(retried).isEqualTo(1)
    }

    @Test
    fun `error view for search`() {
        show { ErrorView(ErrorKind.SEARCH, {}, it) }
        assertText("Couldn't search")
    }

    @Test
    fun `append error footer fires retry`() {
        var retried = 0
        show { AppendErrorFooter({ retried++ }, it) }

        rule.onNodeWithText("Try again").performClick()
        assertThat(retried).isEqualTo(1)
    }

    // --- Font scale ---

    @Test
    fun `at 200 percent font a short state scrolls and its button stays reachable`() {
        show(fontScale = 2f, modifier = Modifier.height(240.dp)) {
            EmptyView(
                EmptyKind.NO_RESULTS,
                it,
                query = "rahul",
                tab = Tab.PHOTOS,
                dateFilterLabel = "Last 7 days",
            )
        }

        rule.onNodeWithText("Search all types").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Clear date filter").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `at 200 percent font the permission banner keeps its actions visible`() {
        show(fontScale = 2f) {
            PermissionBanner(PermissionBanner(listOf(Group.MESSAGES)), {}, {}, it)
        }

        rule.onNodeWithText("Allow access").assertIsDisplayed()
        rule.onNodeWithText("Not now").assertIsDisplayed()
    }
}
