package com.kuriousdragon.smartindex.feature.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.designsystem.theme.SmartIndexTheme
import com.kuriousdragon.smartindex.feature.home.format.DateLabels
import com.kuriousdragon.smartindex.feature.home.model.DateFilter
import com.kuriousdragon.smartindex.feature.home.testing.TestEnv
import com.kuriousdragon.smartindex.feature.home.testing.call
import com.kuriousdragon.smartindex.feature.home.testing.document
import com.kuriousdragon.smartindex.feature.home.testing.recording
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SheetsTest {
    @get:Rule
    val rule = createComposeRule()

    private val log = mutableListOf<String>()

    private fun themed(content: @Composable () -> Unit) {
        rule.setContent { SmartIndexTheme(darkTheme = false, content = content) }
    }

    private fun text(value: String) = rule.onNodeWithText(value)

    private fun gone(value: String) = rule.onNodeWithText(value).assertDoesNotExist()

    // --- File sheet ---

    @Test
    fun `file sheet shows header and exactly Open and Open with`() {
        val item = document(1)
        themed { FileSheet(item, DateLabels.fileSheetHeader(item, TestEnv.env), {}, {}, {}) }

        rule.onNodeWithTag(HomeTestTags.SHEET_FILE).assertIsDisplayed()
        text("doc1.pdf").assertIsDisplayed()
        text("Download · 2.0 KB · Tue 3 Nov, 09:15").assertIsDisplayed()
        text("Open").assertIsDisplayed()
        text("Opens in your default app for this file type").assertIsDisplayed()
        text("Open with…").assertIsDisplayed()
        text("Choose which app opens it").assertIsDisplayed()
    }

    @Test
    fun `file sheet taps dismiss first then call the action`() {
        val item = document(1)
        themed {
            FileSheet(item, "h", { log += "open" }, { log += "with" }, { log += "dismiss" })
        }

        text("Open").performClick()
        text("Open with…").performClick()
        assertThat(log).containsExactly("dismiss", "open", "dismiss", "with").inOrder()
    }

    // --- Call sheet ---

    private fun callSheet(
        known: Boolean,
        hasNumber: Boolean,
        hasRecording: Boolean,
        density: Density? = null,
    ) {
        val base = call(7, linkedRecordingId = if (hasRecording) 9L else null)
        val item = base.copy(
            contactName = if (known) "Rahul" else null,
            number = if (hasNumber) "+919812345678" else null,
            title = if (known) "Rahul" else base.number ?: "Unknown",
        )
        themed {
            val sheet = @Composable {
                CallSheet(
                    item = item,
                    header = "Incoming · 1 min 5 s · Tue 3 Nov, 09:15",
                    known = known,
                    hasNumber = hasNumber,
                    hasRecording = hasRecording,
                    onCallBack = { log += "call" },
                    onOpenContact = { log += "contact" },
                    onAddContact = { log += "add" },
                    onPlayRecording = { log += "play" },
                    onDismiss = { log += "dismiss" },
                )
            }
            if (density == null) sheet() else CompositionLocalProvider(LocalDensity provides density) { sheet() }
        }
    }

    @Test
    fun `known contact with number and recording shows all four rows`() {
        callSheet(known = true, hasNumber = true, hasRecording = true)

        rule.onNodeWithTag(HomeTestTags.SHEET_CALL).assertIsDisplayed()
        text("Rahul").assertIsDisplayed()
        text("Incoming · 1 min 5 s · Tue 3 Nov, 09:15").assertIsDisplayed()
        text("Call back").assertIsDisplayed()
        text("+919812345678").assertIsDisplayed()
        text("Open contact").assertIsDisplayed()
        gone("Add to contacts")
        text("Play recording").assertIsDisplayed()
        text("1:05").assertIsDisplayed()
    }

    @Test
    fun `unknown number without recording offers Add to contacts and no Open contact or Play`() {
        callSheet(known = false, hasNumber = true, hasRecording = false)

        text("Call back").assertIsDisplayed()
        text("Add to contacts").assertIsDisplayed()
        text("Opens your Contacts app").assertIsDisplayed()
        gone("Open contact")
        gone("Play recording")
    }

    @Test
    fun `call sheet without a number hides Call back`() {
        callSheet(known = true, hasNumber = false, hasRecording = false)

        gone("Call back")
        text("Open contact").assertIsDisplayed()
    }

    @Test
    fun `call sheet taps dismiss first then call the right callback`() {
        callSheet(known = true, hasNumber = true, hasRecording = true)

        text("Call back").performClick()
        text("Open contact").performClick()
        text("Play recording").performClick()
        assertThat(log).containsExactly("dismiss", "call", "dismiss", "contact", "dismiss", "play").inOrder()
    }

    @Test
    fun `add to contacts tap calls onAddContact`() {
        callSheet(known = false, hasNumber = false, hasRecording = false)

        text("Add to contacts").performClick()
        assertThat(log).containsExactly("dismiss", "add").inOrder()
    }

    @Test
    fun `call sheet fits 200 percent font`() {
        callSheet(known = true, hasNumber = true, hasRecording = true, density = Density(1f, fontScale = 2f))

        text("Call back").assertIsDisplayed()
        text("Open contact").assertIsDisplayed()
        text("Play recording").assertIsDisplayed()
    }

    // --- Recording sheet ---

    private fun recordingSheet(playing: Boolean, density: Density? = null) {
        val item = recording(3)
        themed {
            val sheet = @Composable {
                RecordingSheet(
                    item = item,
                    header = "Call recording · Tue 3 Nov, 09:15",
                    isCurrentAndPlaying = playing,
                    onPlayPause = { log += "toggle" },
                    onOpenWith = { log += "with" },
                    onDismiss = { log += "dismiss" },
                )
            }
            if (density == null) sheet() else CompositionLocalProvider(LocalDensity provides density) { sheet() }
        }
    }

    @Test
    fun `recording sheet shows Play when not playing`() {
        recordingSheet(playing = false)

        rule.onNodeWithTag(HomeTestTags.SHEET_RECORDING).assertIsDisplayed()
        text("Rahul").assertIsDisplayed()
        text("Play").assertIsDisplayed()
        gone("Pause")
        text("Open with…").assertIsDisplayed()
    }

    @Test
    fun `recording sheet shows Pause when it is the current playing recording`() {
        recordingSheet(playing = true)

        text("Pause").assertIsDisplayed()
        gone("Play")
    }

    @Test
    fun `recording sheet taps dismiss first then act and fit 200 percent font`() {
        recordingSheet(playing = false, density = Density(1f, fontScale = 2f))

        text("Play").performClick()
        text("Open with…").performClick()
        assertThat(log).containsExactly("dismiss", "toggle", "dismiss", "with").inOrder()
    }

    // --- Date filter sheet ---

    private fun dateSheet(current: DateFilter, density: Density? = null) {
        themed {
            val sheet = @Composable {
                DateFilterSheet(
                    current = current,
                    onPreset = { log += "preset:$it" },
                    onPickDay = { log += "day" },
                    onCustomRange = { log += "range" },
                    onDismiss = { log += "dismiss" },
                )
            }
            if (density == null) sheet() else CompositionLocalProvider(LocalDensity provides density) { sheet() }
        }
    }

    private val presetTags = mapOf(
        HomeTestTags.DATE_PRESET_ANY to DateFilter.AnyDate,
        HomeTestTags.DATE_PRESET_TODAY to DateFilter.Today,
        HomeTestTags.DATE_PRESET_YESTERDAY to DateFilter.Yesterday,
        HomeTestTags.DATE_PRESET_LAST_7_DAYS to DateFilter.Last7Days,
        HomeTestTags.DATE_PRESET_LAST_30_DAYS to DateFilter.Last30Days,
    )

    @Test
    fun `date sheet shows title subtitle and every option`() {
        dateSheet(DateFilter.AnyDate)

        text("Filter by date").assertIsDisplayed()
        text("Shows items dated within the range, inclusive, in this phone's time zone").assertIsDisplayed()
        presetTags.keys.forEach { rule.onNodeWithTag(it).assertIsDisplayed() }
        text("Pick a day…").assertIsDisplayed()
        text("Custom range…").assertIsDisplayed()
    }

    private fun assertOnlyPresetSelected(tag: String, filter: DateFilter) {
        dateSheet(filter)
        rule.onNodeWithTag(tag).assertIsSelected()
        presetTags.keys.filter { it != tag }.forEach { rule.onNodeWithTag(it).assertIsNotSelected() }
        rule.onAllNodesWithContentDescription("Selected").assertCountEquals(1)
    }

    @Test
    fun `date sheet checks Any date`() = assertOnlyPresetSelected(HomeTestTags.DATE_PRESET_ANY, DateFilter.AnyDate)

    @Test
    fun `date sheet checks Today`() = assertOnlyPresetSelected(HomeTestTags.DATE_PRESET_TODAY, DateFilter.Today)

    @Test
    fun `date sheet checks Yesterday`() =
        assertOnlyPresetSelected(HomeTestTags.DATE_PRESET_YESTERDAY, DateFilter.Yesterday)

    @Test
    fun `date sheet checks Last 7 days`() =
        assertOnlyPresetSelected(HomeTestTags.DATE_PRESET_LAST_7_DAYS, DateFilter.Last7Days)

    @Test
    fun `date sheet checks Last 30 days`() =
        assertOnlyPresetSelected(HomeTestTags.DATE_PRESET_LAST_30_DAYS, DateFilter.Last30Days)

    @Test
    fun `date sheet marks Pick a day for a day filter and Custom range for a range`() {
        dateSheet(DateFilter.Day(LocalDate.of(2026, 11, 1)))
        rule.onAllNodesWithContentDescription("Selected").assertCountEquals(1)
        presetTags.keys.forEach { rule.onNodeWithTag(it).assertIsNotSelected() }
    }

    @Test
    fun `date sheet range filter checks Custom range`() {
        dateSheet(DateFilter.Range(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3)))
        rule.onAllNodesWithContentDescription("Selected").assertCountEquals(1)
        presetTags.keys.forEach { rule.onNodeWithTag(it).assertIsNotSelected() }
    }

    @Test
    fun `date sheet taps dismiss first then report the choice`() {
        dateSheet(DateFilter.AnyDate)

        rule.onNodeWithTag(HomeTestTags.DATE_PRESET_LAST_7_DAYS).performClick()
        text("Pick a day…").performClick()
        text("Custom range…").performClick()
        assertThat(log)
            .containsExactly("dismiss", "preset:${DateFilter.Last7Days}", "dismiss", "day", "dismiss", "range")
            .inOrder()
    }

    @Test
    fun `date sheet preset taps report each preset`() {
        dateSheet(DateFilter.AnyDate)

        presetTags.keys.forEach { rule.onNodeWithTag(it).performClick() }
        assertThat(log.filter { it.startsWith("preset:") }).containsExactly(
            "preset:${DateFilter.AnyDate}",
            "preset:${DateFilter.Today}",
            "preset:${DateFilter.Yesterday}",
            "preset:${DateFilter.Last7Days}",
            "preset:${DateFilter.Last30Days}",
        ).inOrder()
    }

    @Test
    fun `date sheet fits 200 percent font`() {
        dateSheet(DateFilter.Today, density = Density(1f, fontScale = 2f))

        rule.onNodeWithTag(HomeTestTags.DATE_PRESET_TODAY).assertIsDisplayed()
        text("Custom range…").assertExists()
    }
}
