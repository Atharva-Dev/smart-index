package com.kuriousdragon.smartindex.feature.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.designsystem.theme.SmartIndexTheme
import com.kuriousdragon.smartindex.feature.home.model.DateFilter
import java.time.LocalDate
import java.util.TimeZone
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class PickersTest {
    @get:Rule
    val rule = createComposeRule()

    private val originalZone = TimeZone.getDefault()
    private val today = LocalDate.of(2026, 11, 3)

    @After
    fun restoreZone() = TimeZone.setDefault(originalZone)

    private fun themed(content: @Composable () -> Unit) {
        rule.setContent { SmartIndexTheme(darkTheme = false, content = content) }
    }

    private fun day(label: String): SemanticsMatcher = hasText(label)

    // --- UTC conversion ---

    @Test
    fun `utc midnight converts to the same local date in any device zone and back`() {
        listOf("Pacific/Kiritimati", "Pacific/Pago_Pago", "UTC").forEach { id ->
            TimeZone.setDefault(TimeZone.getTimeZone(id))
            val date = LocalDate.of(2026, 11, 1)
            val millis = date.toUtcMillis()
            assertThat(millis).isEqualTo(1_793_491_200_000L)
            assertThat(millis.utcMillisToLocalDate()).isEqualTo(date)
            assertThat((millis + 86_399_999L).utcMillisToLocalDate()).isEqualTo(date)
            assertThat((millis - 1L).utcMillisToLocalDate()).isEqualTo(date.minusDays(1))
        }
    }

    @Test
    fun `selectable dates stop after today and have no lower bound`() {
        val selectable = selectableUpTo(today)
        assertThat(selectable.isSelectableDate(today.toUtcMillis())).isTrue()
        assertThat(selectable.isSelectableDate(today.plusDays(1).toUtcMillis())).isFalse()
        assertThat(selectable.isSelectableDate(LocalDate.of(1990, 1, 1).toUtcMillis())).isTrue()
        assertThat(selectable.isSelectableYear(2026)).isTrue()
        assertThat(selectable.isSelectableYear(2027)).isFalse()
    }

    // --- Day picker ---

    private fun dayPicker(
        initial: LocalDate = LocalDate.of(2026, 11, 1),
        shortcut: Boolean = true,
        results: MutableList<LocalDate>,
        dismissed: MutableList<String> = mutableListOf(),
        density: Density? = null,
    ) = themed {
        val dialog = @Composable {
            DayPickerDialog(
                initial = initial,
                today = today,
                title = "Go to date",
                showTodayShortcut = shortcut,
                onConfirm = { results += it },
                onDismiss = { dismissed += "dismiss" },
            )
        }
        if (density == null) dialog() else CompositionLocalProvider(LocalDensity provides density) { dialog() }
    }

    @Test
    fun `day picker shows headline Today shortcut Cancel and OK`() {
        dayPicker(results = mutableListOf())

        rule.onNodeWithText("Go to date").assertIsDisplayed()
        rule.onNodeWithText("Today").assertIsDisplayed()
        rule.onNodeWithText("Cancel").assertIsDisplayed()
        rule.onNodeWithText("OK").assertIsDisplayed()
    }

    @Test
    fun `day picker hides the Today shortcut when not requested`() {
        dayPicker(shortcut = false, results = mutableListOf())

        rule.onNodeWithText("Today").assertDoesNotExist()
    }

    @Test
    fun `day picker OK returns the initial date`() {
        val results = mutableListOf<LocalDate>()
        dayPicker(results = results)

        rule.onNodeWithText("OK").performClick()
        assertThat(results).containsExactly(LocalDate.of(2026, 11, 1))
    }

    @Test
    fun `day picker OK round trips in UTC plus 14`() = roundTripIn("Pacific/Kiritimati")

    @Test
    fun `day picker OK round trips in UTC minus 11`() = roundTripIn("Pacific/Pago_Pago")

    private fun roundTripIn(zoneId: String) {
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
        val results = mutableListOf<LocalDate>()
        dayPicker(initial = LocalDate.of(2026, 11, 1), results = results)

        rule.onNodeWithText("OK").performClick()
        assertThat(results).containsExactly(LocalDate.of(2026, 11, 1))
    }

    @Test
    fun `day picker Today shortcut jumps to today and OK returns it`() {
        val results = mutableListOf<LocalDate>()
        dayPicker(results = results)

        rule.onNodeWithText("Today").performClick()
        rule.onNodeWithText("OK").performClick()
        assertThat(results).containsExactly(today)
    }

    @Test
    fun `day picker picking a past day returns it`() {
        val results = mutableListOf<LocalDate>()
        dayPicker(results = results)

        rule.onNode(day("Monday, November 2, 2026")).performClick()
        rule.onNodeWithText("OK").performClick()
        assertThat(results).containsExactly(LocalDate.of(2026, 11, 2))
    }

    @Test
    fun `day picker future days are disabled and today is not`() {
        dayPicker(results = mutableListOf())

        rule.onNode(day("Wednesday, November 4, 2026")).assertIsNotEnabled()
        rule.onNode(day("Saturday, November 28, 2026")).assertIsNotEnabled()
        rule.onNode(day("Tuesday, November 3, 2026")).assertIsEnabled()
    }

    @Test
    fun `day picker Cancel dismisses without a result`() {
        val results = mutableListOf<LocalDate>()
        val dismissed = mutableListOf<String>()
        dayPicker(results = results, dismissed = dismissed)

        rule.onNodeWithText("Cancel").performClick()
        assertThat(results).isEmpty()
        assertThat(dismissed).hasSize(1)
    }

    @Test
    fun `day picker buttons stay reachable at 200 percent font`() {
        dayPicker(results = mutableListOf(), density = Density(1f, fontScale = 2f))

        rule.onNodeWithText("Go to date").assertIsDisplayed()
        rule.onNodeWithText("OK").assertExists()
        rule.onNodeWithText("Cancel").assertExists()
    }

    // --- Range picker ---

    private class RangeLog {
        val confirmed = mutableListOf<Pair<LocalDate, LocalDate?>>()
        val events = mutableListOf<String>()
    }

    private fun rangePicker(
        initial: DateFilter.Range? = null,
        log: RangeLog = RangeLog(),
        density: Density? = null,
    ) = themed {
        val dialog = @Composable {
            RangePickerDialog(
                initial = initial,
                today = today,
                onConfirm = { s, e -> log.confirmed += s to e },
                onClear = { log.events += "clear" },
                onDismiss = { log.events += "dismiss" },
            )
        }
        if (density == null) dialog() else CompositionLocalProvider(LocalDensity provides density) { dialog() }
    }

    @Test
    fun `range picker shows the title Clear Cancel and OK and OK is disabled without a start`() {
        rangePicker()

        rule.onNodeWithText("Custom range").assertIsDisplayed()
        rule.onNodeWithText("Clear").assertIsDisplayed()
        rule.onNodeWithText("Cancel").assertIsDisplayed()
        rule.onNodeWithText("OK").assertIsNotEnabled()
    }

    @Test
    fun `range picker headline shows the initial range live label`() {
        rangePicker(initial = DateFilter.Range(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3)))

        rule.onNode(hasText("1 – 3 Nov 2026")).assertIsDisplayed()
        rule.onNodeWithText("OK").assertIsEnabled()
    }

    @Test
    fun `range picker headline updates when days are picked`() {
        rangePicker()

        rule.onNode(day("Sunday, November 1, 2026")).performClick()
        rule.onNode(hasText("Sun, 1 Nov")).assertIsDisplayed()
        rule.onNode(day("Tuesday, November 3, 2026")).performClick()
        rule.onNode(hasText("1 – 3 Nov 2026")).assertIsDisplayed()
    }

    @Test
    fun `range picker OK returns start and end`() {
        val log = RangeLog()
        rangePicker(initial = DateFilter.Range(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3)), log = log)

        rule.onNodeWithText("OK").performClick()
        assertThat(log.confirmed).containsExactly(LocalDate.of(2026, 11, 1) to LocalDate.of(2026, 11, 3))
    }

    @Test
    fun `range picker OK with only a start returns start and null end`() {
        val log = RangeLog()
        rangePicker(log = log)

        rule.onNode(day("Sunday, November 1, 2026")).performClick()
        rule.onNodeWithText("OK").performClick()
        assertThat(log.confirmed).containsExactly(LocalDate.of(2026, 11, 1) to null)
    }

    @Test
    fun `range picker Clear and Cancel call their callbacks without a result`() {
        val log = RangeLog()
        rangePicker(initial = DateFilter.Range(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3)), log = log)

        rule.onNodeWithText("Clear").performClick()
        rule.onNodeWithText("Cancel").performClick()
        assertThat(log.events).containsExactly("clear", "dismiss").inOrder()
        assertThat(log.confirmed).isEmpty()
    }

    @Test
    fun `range picker future days are disabled`() {
        rangePicker()

        rule.onNode(day("Wednesday, November 4, 2026")).assertIsNotEnabled()
        rule.onNode(day("Tuesday, November 3, 2026")).assertIsEnabled()
    }

    @Test
    fun `range picker round trips a range in UTC plus 14`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
        val log = RangeLog()
        rangePicker(initial = DateFilter.Range(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3)), log = log)

        rule.onNodeWithText("OK").performClick()
        assertThat(log.confirmed).containsExactly(LocalDate.of(2026, 11, 1) to LocalDate.of(2026, 11, 3))
    }

    @Test
    fun `range picker buttons stay reachable at 200 percent font`() {
        rangePicker(density = Density(1f, fontScale = 2f))

        rule.onNodeWithText("Custom range").assertIsDisplayed()
        rule.onNodeWithText("Clear").assertExists()
        rule.onNodeWithText("OK").assertExists()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h400dp")
    fun `range picker in a short window is full screen and keeps its buttons`() {
        val log = RangeLog()
        rangePicker(initial = DateFilter.Range(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3)), log = log)

        rule.onNodeWithText("OK").assertIsDisplayed().performClick()
        assertThat(log.confirmed).hasSize(1)
    }
}
