package com.kuriousdragon.smartindex.feature.home.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.designsystem.theme.LocalReducedMotion
import com.kuriousdragon.smartindex.core.designsystem.theme.SmartIndexTheme
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.Field
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.MsgKind
import com.kuriousdragon.smartindex.core.model.ResultItem
import com.kuriousdragon.smartindex.core.model.ThumbnailRequest
import com.kuriousdragon.smartindex.feature.home.format.RowAction
import com.kuriousdragon.smartindex.feature.home.format.RowFormatter
import com.kuriousdragon.smartindex.feature.home.format.RowUi
import com.kuriousdragon.smartindex.feature.home.testing.TestEnv
import com.kuriousdragon.smartindex.feature.home.testing.call
import com.kuriousdragon.smartindex.feature.home.testing.document
import com.kuriousdragon.smartindex.feature.home.testing.message
import com.kuriousdragon.smartindex.feature.home.testing.otherFile
import com.kuriousdragon.smartindex.feature.home.testing.photo
import com.kuriousdragon.smartindex.feature.home.testing.recording
import com.kuriousdragon.smartindex.feature.home.testing.resultItem
import com.kuriousdragon.smartindex.feature.home.testing.video
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ListRowTest {
    @get:Rule
    val rule = createComposeRule()

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private fun ui(item: ResultItem): RowUi = RowFormatter.format(item, TestEnv.env, resources)

    private val clicks = mutableListOf<String>()

    @Composable
    private fun Row(
        ui: RowUi,
        playing: Boolean = false,
        isCurrent: Boolean = false,
        thumbnail: ThumbnailRequest? = null,
    ) = ListRow(
        ui = ui,
        playing = playing,
        isCurrent = isCurrent,
        onClick = { clicks += "row" },
        onMore = { clicks += "more" },
        onPlayPause = { clicks += "play" },
        onAction = { clicks += "action:$it" },
        thumbnail = thumbnail,
    )

    private data class Spec(
        val row: RowUi,
        val dark: Boolean,
        val playing: Boolean,
        val isCurrent: Boolean,
        val thumbnail: ThumbnailRequest?,
        val fontScale: Float?,
    )

    private var spec by mutableStateOf<Spec?>(null)
    private var composed = false

    /** Shows [item]; calling it again swaps the content of the same composition. */
    private fun show(
        item: ResultItem,
        dark: Boolean,
        playing: Boolean = false,
        isCurrent: Boolean = false,
        thumbnail: ThumbnailRequest? = null,
        fontScale: Float? = null,
    ): RowUi {
        val row = ui(item)
        spec = Spec(row, dark, playing, isCurrent, thumbnail, fontScale)
        if (!composed) {
            composed = true
            rule.setContent {
                val s = spec ?: return@setContent
                val density = LocalDensity.current
                val d = if (s.fontScale == null) density else Density(density.density, s.fontScale)
                CompositionLocalProvider(LocalDensity provides d) {
                    SmartIndexTheme(darkTheme = s.dark) {
                        Row(s.row, s.playing, s.isCurrent, s.thumbnail)
                    }
                }
            }
        }
        rule.waitForIdle()
        return row
    }

    private val samples: List<ResultItem> = listOf(
        photo(1),
        video(2),
        document(3),
        otherFile(4),
        call(5, linkedRecordingId = 9),
        recording(6),
        message(7),
    )

    private fun rowNode(label: String) = rule.onNode(hasContentDescription(label))

    private fun texts(label: String): List<String> = rowNode(label).fetchSemanticsNode().let { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
    }

    @Test
    fun `each type renders title meta time and trailing in light and dark`() {
        for (dark in listOf(false, true)) {
            for (item in samples) {
                clicks.clear()
                val row = show(item, dark)
                rule.onNodeWithTag(HomeTestTags.ROW).assertIsDisplayed()
                val shown = texts(row.a11yLabel)
                assertThat(shown).contains(row.title.text)
                assertThat(shown).contains(row.time)
                assertThat(shown).contains(row.meta.text)
                row.snippet?.let { assertThat(shown).contains(it.text) }
                when (item.type) {
                    ItemType.PHOTO, ItemType.VIDEO, ItemType.DOCUMENT, ItemType.OTHER_FILE ->
                        rule.onNodeWithContentDescription("More actions").assertIsDisplayed()

                    ItemType.CALL ->
                        rule.onNodeWithContentDescription("Play recording of call with Rahul").assertIsDisplayed()

                    ItemType.RECORDING ->
                        rule.onNodeWithContentDescription("Play recording of call with Rahul")
                            .assertIsDisplayed()

                    ItemType.MESSAGE -> {
                        rule.onNodeWithContentDescription("More actions").assertDoesNotExist()
                        rule.onNode(hasContentDescription("Pause recording")).assertDoesNotExist()
                    }
                }
                if (item.type == ItemType.DOCUMENT || item.type == ItemType.OTHER_FILE ||
                    item.type == ItemType.RECORDING
                ) {
                    rule.onNode(hasText(row.extLabel!!, substring = false), useUnmergedTree = true).assertIsDisplayed()
                }
                if (item.type == ItemType.VIDEO) {
                    rule.onNode(hasText("0:12"), useUnmergedTree = true).assertIsDisplayed()
                }
            }
        }
    }

    @Test
    fun `call without a recording has an empty trailing slot`() {
        show(call(1), dark = false)
        rule.onNode(hasContentDescription("Pause recording"), useUnmergedTree = true).assertDoesNotExist()
        rule.onNode(
            SemanticsMatcher("play button") { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)
                    ?.any { it.startsWith("Play recording of call") } == true
            },
            useUnmergedTree = true,
        ).assertDoesNotExist()
    }

    @Test
    fun `highlight spans are present on the matched title meta and snippet substrings`() {
        val item = resultItem(
            id = 7,
            type = ItemType.MESSAGE,
            title = "Rahul",
            direction = Direction.RECEIVED,
            msgKind = MsgKind.SMS,
            contactName = "Rahul",
            snippet = "See you at the hotel",
            matches = mapOf(Field.CONTACT_NAME to listOf(0..2), Field.SNIPPET to listOf(11..15)),
        )
        val row = show(item, dark = false)
        val spans = rowNode(row.a11yLabel).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text).orEmpty()
        val title = spans.first { it.text == "Rahul" }
        assertThat(title.spanStyles.map { it.start to it.end }).containsExactly(0 to 3)
        val snippet = spans.first { it.text == "See you at the hotel" }
        assertThat(snippet.spanStyles.map { it.start to it.end }).containsExactly(11 to 16)
    }

    @Test
    fun `meta highlight is applied to the meta text`() {
        val item = resultItem(
            id = 8,
            type = ItemType.PHOTO,
            title = "IMG_8.jpg",
            folder = "DCIM/Camera",
            matches = mapOf(Field.FOLDER to listOf(5..7)),
        )
        val row = show(item, dark = false)
        val meta = rowNode(row.a11yLabel).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text).orEmpty().first { it.text == row.meta.text }
        assertThat(meta.spanStyles).isNotEmpty()
    }

    @Test
    fun `playing row swaps the play triangle for pause and paused current keeps play`() {
        val item = recording(6)
        show(item, dark = false, playing = true, isCurrent = true)
        rule.onNodeWithContentDescription("Pause recording").assertIsDisplayed()
        rule.onNode(hasContentDescription("Play recording of call with Rahul"))
            .assertDoesNotExist()
    }

    @Test
    fun `paused current row shows play`() {
        show(recording(6), dark = true, playing = false, isCurrent = true)
        rule.onNodeWithContentDescription("Play recording of call with Rahul").assertIsDisplayed()
        rule.onNode(hasContentDescription("Pause recording")).assertDoesNotExist()
    }

    @Test
    fun `thumbnail failure leaves the tile visible`() {
        val item = resultItem(
            id = 1,
            type = ItemType.PHOTO,
            title = "IMG_1.jpg",
            folder = "DCIM/Camera",
            contentUri = "content://media/external/images/media/1",
        )
        val request = ThumbnailRequest.of(item)
        assertThat(request).isNotNull()
        show(item, dark = false, thumbnail = request)
        rule.waitForIdle()
        rule.onNodeWithTag(HomeTestTags.ROW).assertIsDisplayed()
        // The icon of the tile is decorative; the row and its label stay intact when the load fails.
        rowNode(ui(item).a11yLabel).assertIsDisplayed()
    }

    @Test
    fun `semantic label click label and custom actions per type`() {
        val expectedClickLabel = mapOf(
            ItemType.PHOTO to "Open",
            ItemType.VIDEO to "Open",
            ItemType.DOCUMENT to "Open",
            ItemType.OTHER_FILE to "Open",
            ItemType.CALL to "Show actions",
            ItemType.RECORDING to "Show actions",
            ItemType.MESSAGE to "Open in Messages",
        )
        for (item in samples) {
            val row = show(item, dark = false)
            val config = rowNode(row.a11yLabel).fetchSemanticsNode().config
            assertThat(config.getOrNull(SemanticsProperties.ContentDescription)).contains(row.a11yLabel)
            assertThat(config.getOrNull(SemanticsActions.OnClick)?.label).isEqualTo(expectedClickLabel[item.type])
            val actions = config.getOrNull(SemanticsActions.CustomActions).orEmpty()
            assertThat(actions.map { it.label }).isEqualTo(row.actions.map { RowActionLabels.of(it) })
        }
    }

    @Test
    fun `custom actions invoke onAction and play shows pause while playing`() {
        val row = show(recording(6), dark = false, playing = true, isCurrent = true)
        val actions = rowNode(row.a11yLabel).fetchSemanticsNode().config
            .getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertThat(actions.map { it.label }).containsExactly("Pause recording", "Open with").inOrder()
        rule.runOnUiThread { actions.first().action() }
        rule.runOnIdle { assertThat(clicks).containsExactly("action:PLAY") }
    }

    @Test
    fun `more and play are separate nodes of at least 48 dp`() {
        show(photo(1), dark = false)
        rule.onNodeWithContentDescription("More actions").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        show(recording(6), dark = false)
        rule.onNodeWithContentDescription("Play recording of call with Rahul")
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `clicking the box and the trailing button fire different callbacks`() {
        val photoRow = show(photo(1), dark = false)
        rowNode(photoRow.a11yLabel).performClick()
        rule.onNodeWithContentDescription("More actions").performClick()
        rule.runOnIdle { assertThat(clicks).containsExactly("row", "more").inOrder() }
    }

    @Test
    fun `play button fires play pause and not row click`() {
        clicks.clear()
        show(recording(6), dark = false)
        rule.onNodeWithContentDescription("Play recording of call with Rahul").performClick()
        rule.runOnIdle { assertThat(clicks).containsExactly("play") }
    }

    @Test
    fun `font scale 2 keeps one title line and does not clip`() {
        val item = resultItem(
            id = 1,
            type = ItemType.DOCUMENT,
            title = "A very long document name that cannot fit on one line at all.pdf",
            folder = "Download",
            ext = "pdf",
            sizeBytes = 2_048,
        )
        val row = show(item, dark = false, fontScale = 2.0f)
        rule.waitForIdle()
        rule.onNodeWithTag(HomeTestTags.ROW).assertIsDisplayed().assertHeightIsAtLeast(72.dp)
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNode(hasText(row.title.text), useUnmergedTree = true).fetchSemanticsNode().config
            .getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        assertThat(layouts.first().lineCount).isEqualTo(1)
        // The time moved under the title and is still shown in full.
        rule.onNode(hasText(row.time), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `section header is a heading`() {
        rule.setContent { SmartIndexTheme(darkTheme = false) { SectionHeader("Morning") } }
        rule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
        ).assertIsDisplayed()
        rule.onNodeWithText("Morning").assertIsDisplayed()
    }

    @Test
    fun `skeleton has no animation under reduced motion`() {
        var state: State<Float>? = null
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                state = rememberShimmerProgress(LocalReducedMotion.current)
                SmartIndexTheme(darkTheme = false) { SkeletonRow() }
            }
        }
        rule.mainClock.advanceTimeBy(600)
        assertThat(state!!.value).isEqualTo(0f)
        rule.onNodeWithTag(HomeTestTags.SKELETON).assertIsDisplayed()
    }

    @Test
    fun `skeleton shimmers when motion is allowed`() {
        var state: State<Float>? = null
        rule.mainClock.autoAdvance = false
        rule.setContent {
            state = rememberShimmerProgress(false)
            SmartIndexTheme(darkTheme = false) { SkeletonRow() }
        }
        rule.mainClock.advanceTimeBy(300)
        assertThat(state!!.value).isGreaterThan(0f)
    }

    private object RowActionLabels {
        fun of(action: RowAction): String = when (action) {
            RowAction.OPEN_WITH, RowAction.OPEN_WITH_AUDIO -> "Open with"
            RowAction.MORE_ACTIONS -> "More actions"
            RowAction.CALL_BACK -> "Call back"
            RowAction.OPEN_CONTACT -> "Open contact"
            RowAction.ADD_CONTACT -> "Add to contacts"
            RowAction.PLAY_RECORDING -> "Play recording"
            RowAction.PLAY -> "Play"
        }
    }
}
