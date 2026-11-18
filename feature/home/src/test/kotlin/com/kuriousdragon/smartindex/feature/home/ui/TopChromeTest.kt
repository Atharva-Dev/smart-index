package com.kuriousdragon.smartindex.feature.home.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.designsystem.theme.SmartIndexTheme
import com.kuriousdragon.smartindex.feature.home.model.DateFilter
import com.kuriousdragon.smartindex.feature.home.testing.TestEnv
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TopChromeTest {
    @get:Rule
    val rule = createComposeRule()

    private val env = TestEnv.env
    private val today = TestEnv.today

    private fun themed(content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { SmartIndexTheme(darkTheme = false, content = content) }
    }

    private fun description(tag: String): String? =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.first()

    // --- Search bar and field ---

    @Test
    fun `idle bar is a 56 dp button that fires onClick and carries the search description`() {
        var clicks = 0
        themed { SearchBarIdle(onClick = { clicks++ }, onOverflowStatus = {}, onOverflowPermissions = {}) }

        rule.onNodeWithTag(HomeTestTags.SEARCH_BAR).assertHeightIsAtLeast(56.dp).performClick()
        assertThat(clicks).isEqualTo(1)
        assertThat(description(HomeTestTags.SEARCH_BAR)).isEqualTo("Search messages, files, calls")
        rule.onNodeWithTag(HomeTestTags.SEARCH_FIELD).assertDoesNotExist()
    }

    @Test
    fun `overflow menu items fire the right callbacks and close the menu`() {
        val calls = mutableListOf<String>()
        themed {
            SearchBarIdle(
                onClick = { calls += "bar" },
                onOverflowStatus = { calls += "status" },
                onOverflowPermissions = { calls += "permissions" },
            )
        }

        rule.onNodeWithContentDescription("More options").performClick()
        rule.onNodeWithText("Index status").assertIsDisplayed().performClick()
        rule.onNodeWithText("Index status").assertDoesNotExist()
        rule.onNodeWithContentDescription("More options").performClick()
        rule.onNodeWithText("Permissions").performClick()

        assertThat(calls).containsExactly("status", "permissions").inOrder()
    }

    @Test
    fun `active field shows the hint when empty and hides the clear button`() {
        themed { SearchField(query = "", onQueryChange = {}, onSearch = {}, onClear = {}, onBack = {}) }

        rule.onNodeWithTag(HomeTestTags.SEARCH_FIELD).assertIsDisplayed()
        rule.onNodeWithText("Search messages, files, calls…").assertIsDisplayed()
        rule.onNodeWithTag(HomeTestTags.SEARCH_CLEAR).assertDoesNotExist()
        rule.onNodeWithTag(HomeTestTags.SEARCH_BAR).assertDoesNotExist()
    }

    @Test
    fun `typing reports the text and the clear button appears only with text`() {
        var query by mutableStateOf("")
        val typed = mutableListOf<String>()
        var cleared = 0
        themed {
            SearchField(
                query = query,
                onQueryChange = {
                    typed += it
                    query = it
                },
                onSearch = {},
                onClear = {
                    cleared++
                    query = ""
                },
                onBack = {},
            )
        }

        rule.onNodeWithTag(HomeTestTags.SEARCH_CLEAR).assertDoesNotExist()
        rule.onNodeWithTag(HomeTestTags.SEARCH_FIELD).performTextInput("ra")
        assertThat(typed.last()).isEqualTo("ra")
        rule.onNodeWithTag(HomeTestTags.SEARCH_CLEAR).assertIsDisplayed().performClick()
        assertThat(cleared).isEqualTo(1)
        rule.onNodeWithTag(HomeTestTags.SEARCH_CLEAR).assertDoesNotExist()
        rule.onNodeWithTag(HomeTestTags.SEARCH_FIELD).assertIsDisplayed()
    }

    @Test
    fun `the search key action calls onSearch and back calls onBack`() {
        var searches = 0
        var backs = 0
        themed {
            SearchField(
                query = "rahul",
                onQueryChange = {},
                onSearch = { searches++ },
                onClear = {},
                onBack = { backs++ },
            )
        }

        rule.onNodeWithTag(HomeTestTags.SEARCH_FIELD).performImeAction()
        assertThat(searches).isEqualTo(1)
        rule.onNodeWithContentDescription("Back").performClick()
        assertThat(backs).isEqualTo(1)
    }

    // --- Date navigator ---

    private fun navigator(
        date: LocalDate,
        canGoNext: Boolean = true,
        onPrev: () -> Unit = {},
        onNext: () -> Unit = {},
        onDate: () -> Unit = {},
    ) = themed {
        DateNavigator(
            date = date,
            env = env,
            canGoNext = canGoNext,
            onPrev = onPrev,
            onNext = onNext,
            onDateClick = onDate,
        )
    }

    @Test
    fun `date button label is Today, Yesterday or the full date`() {
        var date by mutableStateOf(today)
        themed {
            DateNavigator(
                date = date,
                env = env,
                canGoNext = true,
                onPrev = {},
                onNext = {},
                onDateClick = {},
            )
        }

        rule.onNodeWithText("Today · Tue, 3 Nov", useUnmergedTree = true).assertIsDisplayed()
        date = today.minusDays(1)
        rule.onNodeWithText("Yesterday · Mon, 2 Nov", useUnmergedTree = true).assertIsDisplayed()
        date = LocalDate.of(2026, 10, 29)
        rule.onNodeWithText("Thu, 29 Oct 2026", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `navigator semantics and tags`() {
        navigator(today)

        assertThat(description(HomeTestTags.DATE_BUTTON)).isEqualTo("Showing Tuesday, 3 November 2026. Change date")
        rule.onNodeWithContentDescription("Previous day").assertIsDisplayed()
        rule.onNodeWithContentDescription("Next day").assertIsDisplayed()
        rule.onNodeWithTag(HomeTestTags.PREV_DAY).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `arrows and date button fire their callbacks`() {
        val calls = mutableListOf<String>()
        navigator(
            date = today.minusDays(2),
            onPrev = { calls += "prev" },
            onNext = { calls += "next" },
            onDate = { calls += "date" },
        )

        rule.onNodeWithTag(HomeTestTags.PREV_DAY).performClick()
        rule.onNodeWithTag(HomeTestTags.NEXT_DAY).performClick()
        rule.onNodeWithTag(HomeTestTags.DATE_BUTTON).performClick()

        assertThat(calls).containsExactly("prev", "next", "date").inOrder()
    }

    @Test
    fun `next is disabled with an unavailable state when there is no next day`() {
        var nexts = 0
        navigator(today, canGoNext = false, onNext = { nexts++ })

        rule.onNodeWithTag(HomeTestTags.NEXT_DAY).assertIsNotEnabled().performClick()
        assertThat(nexts).isEqualTo(0)
        val state = rule.onNodeWithTag(HomeTestTags.NEXT_DAY).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.StateDescription)
        assertThat(state).isEqualTo("unavailable")
        rule.onNodeWithTag(HomeTestTags.PREV_DAY).assertIsEnabled()
    }

    @Test
    fun `next is enabled with no state description when a next day exists`() {
        navigator(today.minusDays(1))

        rule.onNodeWithTag(HomeTestTags.NEXT_DAY).assertIsEnabled()
        val state = rule.onNodeWithTag(HomeTestTags.NEXT_DAY).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.StateDescription)
        assertThat(state).isNull()
    }

    @Test
    fun `200 percent font keeps the date button and shows an ellipsized label`() {
        themed {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                DateNavigator(
                    date = LocalDate.of(2026, 10, 29),
                    env = env,
                    canGoNext = true,
                    onPrev = {},
                    onNext = {},
                    onDateClick = {},
                )
            }
        }

        rule.onNodeWithTag(HomeTestTags.DATE_BUTTON).assertIsDisplayed().assertHeightIsAtLeast(40.dp)
        rule.onNodeWithTag(HomeTestTags.NEXT_DAY).assertIsDisplayed()
        rule.onNodeWithTag(HomeTestTags.PREV_DAY).assertIsDisplayed()
    }

    // --- Date chip ---

    @Test
    fun `idle chip shows Any date and opens the sheet`() {
        var opens = 0
        themed { DateChipRow(DateFilter.AnyDate, env, onOpen = { opens++ }, onClear = {}) }

        rule.onNodeWithTag(HomeTestTags.DATE_CHIP).assertIsDisplayed()
        rule.onNodeWithText("Any date").performClick()
        assertThat(opens).isEqualTo(1)
        rule.onNode(hasContentDescription("Clear date filter")).assertDoesNotExist()
    }

    @Test
    fun `applied chip shows the range label, label tap opens and the X clears`() {
        var opens = 0
        var clears = 0
        themed {
            DateChipRow(
                DateFilter.Range(LocalDate.of(2026, 10, 29), today),
                env,
                onOpen = { opens++ },
                onClear = { clears++ },
            )
        }

        rule.onNodeWithText("29 Oct – 3 Nov 2026").performClick()
        assertThat(opens).isEqualTo(1)
        assertThat(clears).isEqualTo(0)
        rule.onNodeWithContentDescription("Clear date filter").performClick()
        assertThat(clears).isEqualTo(1)
        assertThat(opens).isEqualTo(1)
    }

    @Test
    fun `chip row is at least 48 dp tall and shows preset labels`() {
        themed { DateChipRow(DateFilter.Last7Days, env, onOpen = {}, onClear = {}) }

        rule.onNodeWithText("Last 7 days").assertIsDisplayed()
        rule.onNodeWithTag(HomeTestTags.DATE_CHIP).assertIsDisplayed()
    }
}
