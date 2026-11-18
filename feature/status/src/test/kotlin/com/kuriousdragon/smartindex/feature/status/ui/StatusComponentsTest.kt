package com.kuriousdragon.smartindex.feature.status.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.designsystem.theme.SmartIndexTheme
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.IndexError
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.feature.status.format.StatusFormatter
import com.kuriousdragon.smartindex.feature.status.model.BannerUi
import com.kuriousdragon.smartindex.feature.status.model.PermissionAction
import com.kuriousdragon.smartindex.feature.status.model.PermissionRowUi
import com.kuriousdragon.smartindex.feature.status.model.ReindexUi
import com.kuriousdragon.smartindex.feature.status.model.StatusEnv
import com.kuriousdragon.smartindex.feature.status.model.SubLine
import com.kuriousdragon.smartindex.feature.status.model.TypeRowUi
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StatusComponentsTest {
    @get:Rule
    val rule = createComposeRule()

    private val zone = ZoneId.of("UTC")
    private val now = ZonedDateTime.of(2026, 11, 5, 15, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val formatter = StatusFormatter(StatusEnv(zone, Locale.US, is24Hour = true), now)

    private fun ms(day: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private fun setBanner(banner: BannerUi) {
        rule.setContent { SmartIndexTheme(darkTheme = false) { StatusBanner(banner, formatter) } }
    }

    private fun description(tag: String): String? =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.first()

    private fun hasText(text: String) = rule.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()

    private val progressKey = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    private fun progress(): ProgressBarRangeInfo? =
        rule.onNode(progressKey, useUnmergedTree = true).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ProgressBarRangeInfo)

    private fun textColor(node: SemanticsNode): Color {
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!.invoke(results)
        return results.first().layoutInput.style.color
    }

    // Banner

    @Test
    fun `up to date today shows title and last indexed time`() {
        setBanner(BannerUi.UpToDate(ms(5, 9, 42)))
        hasText("Up to date")
        hasText("Last indexed today at 09:42. Checks for changes every 15 minutes and when you open the app.")
        rule.onNode(progressKey, useUnmergedTree = true).assertDoesNotExistCompat()
        assertThat(bannerRole(BannerUi.UpToDate(1L))).isEqualTo(BannerRole.SURFACE)
    }

    @Test
    fun `up to date yesterday and other day use their own body`() {
        setBanner(BannerUi.UpToDate(ms(4, 21, 5)))
        hasText("Last indexed yesterday at 21:05. Checks for changes every 15 minutes and when you open the app.")
    }

    @Test
    fun `up to date on an older day shows the date`() {
        setBanner(BannerUi.UpToDate(ms(1, 8, 0)))
        hasText("Last indexed on Nov 1, 2026 at 08:00. Checks for changes every 15 minutes and when you open the app.")
    }

    @Test
    fun `up to date without a run says not indexed yet`() {
        setBanner(BannerUi.UpToDate(null))
        hasText("Not indexed yet")
        hasText("Allow access below and Smart Index will start indexing.")
    }

    @Test
    fun `updating shows an indeterminate progress bar`() {
        setBanner(BannerUi.Updating)
        hasText("Updating…")
        hasText("Checking for new and changed items.")
        assertThat(progress()).isEqualTo(ProgressBarRangeInfo.Indeterminate)
        assertThat(bannerRole(BannerUi.Updating)).isEqualTo(BannerRole.SURFACE)
    }

    @Test
    fun `indexing with percent shows the exact percent and determinate progress`() {
        setBanner(BannerUi.Indexing(done = 6_700, total = 18_240, percent = 37))
        hasText("Indexing… 37%")
        hasText("6,700 of 18,240 items. Search works meanwhile; results may be incomplete.")
        assertThat(progress()!!.current).isWithin(0.001f).of(0.37f)
        assertThat(bannerRole(BannerUi.Indexing(1, 2, 50))).isEqualTo(BannerRole.PRIMARY)
    }

    @Test
    fun `indexing without percent is indeterminate`() {
        setBanner(BannerUi.Indexing(done = 0, total = 0, percent = null))
        hasText("Indexing…")
        hasText("Search works meanwhile; results may be incomplete.")
        assertThat(progress()).isEqualTo(ProgressBarRangeInfo.Indeterminate)
    }

    @Test
    fun `live region description uses the 10 percent bucket`() {
        setBanner(BannerUi.Indexing(done = 6_700, total = 18_240, percent = 37))
        val config = rule.onNodeWithTag(StatusTestTags.BANNER).fetchSemanticsNode().config
        assertThat(config.getOrNull(SemanticsProperties.LiveRegion)).isEqualTo(
            androidx.compose.ui.semantics.LiveRegionMode.Polite,
        )
        assertThat(description(StatusTestTags.BANNER))
            .isEqualTo("Indexing… 30%. 6,700 of 18,240 items. Search works meanwhile; results may be incomplete.")
    }

    @Test
    fun `storage full error`() {
        setBanner(BannerUi.Error(IndexError.Kind.STORAGE_FULL, emptySet()))
        hasText("Indexing paused")
        hasText("Storage is almost full. Free up space to continue.")
        assertThat(bannerRole(BannerUi.Error(IndexError.Kind.STORAGE_FULL, emptySet()))).isEqualTo(BannerRole.ERROR)
    }

    @Test
    fun `linking failed error`() {
        setBanner(BannerUi.Error(IndexError.Kind.LINKING_FAILED, emptySet()))
        hasText("Recordings aren't linked yet")
    }

    @Test
    fun `source failed error with one source`() {
        setBanner(BannerUi.Error(IndexError.Kind.SOURCE_FAILED, setOf(Source.MEDIA_STORE)))
        hasText("Some items couldn't be indexed")
        hasText("Couldn't read your photos, videos and files. Smart Index will try again soon.")
    }

    @Test
    fun `source failed error joins two areas and names messages once`() {
        setBanner(BannerUi.Error(IndexError.Kind.SOURCE_FAILED, linkedSetOf(Source.SMS, Source.MMS, Source.CALL_LOG)))
        hasText("Couldn't read your messages and your call history. Smart Index will try again soon.")
    }

    @Test
    fun `source failed error with three areas uses commas`() {
        setBanner(
            BannerUi.Error(
                IndexError.Kind.SOURCE_FAILED,
                linkedSetOf(Source.MEDIA_STORE, Source.CALL_LOG, Source.MMS),
            ),
        )
        hasText(
            "Couldn't read your photos, videos and files, your call history and your messages. " +
                "Smart Index will try again soon.",
        )
    }

    // Type rows

    private fun setRow(row: TypeRowUi) {
        rule.setContent { SmartIndexTheme(darkTheme = false) { TypeRow(row, formatter) } }
    }

    private fun countNode() = rule.onNodeWithTag(StatusTestTags.typeCount(ItemType.PHOTO), useUnmergedTree = true)

    @Test
    fun `type row formats the count with a grouping separator`() {
        setRow(TypeRowUi(ItemType.PHOTO, 18_240, SubLine.None))
        countNode().assertIsDisplayed()
        hasText("18,240")
        hasText("Photos")
        assertThat(description(StatusTestTags.typeRow(ItemType.PHOTO))).isEqualTo("Photos, 18,240 items")
    }

    @Test
    fun `type row shows a dash while the count is unknown`() {
        setRow(TypeRowUi(ItemType.PHOTO, null, SubLine.None))
        hasText("—")
        assertThat(description(StatusTestTags.typeRow(ItemType.PHOTO))).isEqualTo("Photos, counting")
    }

    @Test
    fun `document row shows the kinds sub line`() {
        setRow(TypeRowUi(ItemType.DOCUMENT, 1_204, SubLine.DocumentKinds))
        hasText("PDF, Office and text files")
        assertThat(description(StatusTestTags.typeRow(ItemType.DOCUMENT)))
            .isEqualTo("Documents, 1,204 items, PDF, Office and text files")
    }

    @Test
    fun `recording row shows how many are linked to calls`() {
        setRow(TypeRowUi(ItemType.RECORDING, 2_300, SubLine.RecordingsLinked(1_234)))
        hasText("1,234 linked to calls")
    }

    @Test
    fun `no access row uses the error colour and the not indexed text`() {
        var error = Color.Unspecified
        var variant = Color.Unspecified
        rule.setContent {
            SmartIndexTheme(darkTheme = false) {
                error = MaterialTheme.colorScheme.error
                variant = MaterialTheme.colorScheme.onSurfaceVariant
                TypeRow(TypeRowUi(ItemType.MESSAGE, null, SubLine.NoAccess), formatter)
            }
        }
        val sub = rule.onNodeWithText("No access — not indexed", useUnmergedTree = true)
        sub.assertIsDisplayed()
        assertThat(textColor(sub.fetchSemanticsNode())).isEqualTo(error)
        val count = rule.onNodeWithTag(StatusTestTags.typeCount(ItemType.MESSAGE), useUnmergedTree = true)
        assertThat(textColor(count.fetchSemanticsNode())).isEqualTo(variant)
        assertThat(description(StatusTestTags.typeRow(ItemType.MESSAGE)))
            .isEqualTo("Messages, no access, not indexed")
    }

    @Test
    fun `type row is at least 56 dp tall`() {
        setRow(TypeRowUi(ItemType.CALL, 3, SubLine.None))
        rule.onNodeWithTag(StatusTestTags.typeRow(ItemType.CALL)).assertHeightIsAtLeast(56.dp)
    }

    // Permission rows

    private var allowed = 0

    private fun setPermission(group: Group, state: GroupState, action: PermissionAction) {
        allowed = 0
        rule.setContent {
            SmartIndexTheme(darkTheme = false) {
                PermissionRow(PermissionRowUi(group, state, action), onAllow = { allowed++ })
            }
        }
    }

    @Test
    fun `granted group shows allowed and no button`() {
        setPermission(Group.MESSAGES, GroupState.GRANTED, PermissionAction.NONE)
        hasText("✓ Allowed")
        rule.onNodeWithTag(StatusTestTags.allow(Group.MESSAGES)).assertDoesNotExistCompat()
        assertThat(description(StatusTestTags.permissionRow(Group.MESSAGES))).isEqualTo("Messages, allowed")
    }

    @Test
    fun `denied group shows a tonal Allow button that calls back`() {
        setPermission(Group.CALL_LOG, GroupState.DENIED, PermissionAction.ALLOW)
        val button = rule.onNodeWithTag(StatusTestTags.allow(Group.CALL_LOG))
        button.assertIsEnabled().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        hasText("Allow")
        assertThat(description(StatusTestTags.allow(Group.CALL_LOG))).isEqualTo("Allow Call history")
        assertThat(description(StatusTestTags.permissionRow(Group.CALL_LOG))).isEqualTo("Call history, not allowed")
        button.performClick()
        assertThat(allowed).isEqualTo(1)
    }

    @Test
    fun `partial photos show the selected only text and an Allow all button`() {
        setPermission(Group.PHOTOS_VIDEOS, GroupState.PARTIAL, PermissionAction.ALLOW_ALL)
        hasText("Allowed — selected photos and videos only")
        hasText("Allow all")
        rule.onNodeWithTag(StatusTestTags.allow(Group.PHOTOS_VIDEOS)).performClick()
        assertThat(allowed).isEqualTo(1)
        assertThat(description(StatusTestTags.permissionRow(Group.PHOTOS_VIDEOS)))
            .isEqualTo("Photos & videos, allowed")
    }

    // Header, privacy, re-index

    @Test
    fun `permissions header is tagged and a heading`() {
        rule.setContent { SmartIndexTheme(darkTheme = false) { PermissionsHeader() } }
        val node = rule.onNodeWithTag(StatusTestTags.PERMISSIONS_HEADER)
        node.assertIsDisplayed()
        assertThat(node.fetchSemanticsNode().config.contains(SemanticsProperties.Heading)).isTrue()
        hasText("Permissions")
    }

    @Test
    fun `privacy note shows the statement`() {
        rule.setContent { SmartIndexTheme(darkTheme = false) { PrivacyNote() } }
        hasText(
            "Your index is stored only in Smart Index's private storage on this phone. " +
                "The app has no internet permission and the index is excluded from backups.",
        )
    }

    private var reindexClicks = 0

    private fun setReindex(enabled: Boolean) {
        reindexClicks = 0
        rule.setContent {
            SmartIndexTheme(darkTheme = false) { ReindexSection(ReindexUi(enabled), onClick = { reindexClicks++ }) }
        }
    }

    @Test
    fun `re-index button is enabled when idle and calls back`() {
        setReindex(enabled = true)
        val button = rule.onNodeWithTag(StatusTestTags.REINDEX)
        button.assertIsEnabled().assertHeightIsAtLeast(48.dp)
        hasText("Re-index everything")
        rule.onNodeWithText("Available when indexing finishes.", useUnmergedTree = true).assertDoesNotExistCompat()
        button.performClick()
        assertThat(reindexClicks).isEqualTo(1)
    }

    @Test
    fun `re-index button is disabled with a helper while running`() {
        setReindex(enabled = false)
        rule.onNodeWithTag(StatusTestTags.REINDEX).assertIsNotEnabled()
        hasText("Available when indexing finishes.")
        val state = rule.onNodeWithTag(StatusTestTags.REINDEX).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.StateDescription)
        assertThat(state).isEqualTo("Available when indexing finishes.")
    }

    @Test
    fun `re-index dialog has texts and both buttons`() {
        var confirmed = 0
        var dismissed = 0
        rule.setContent {
            SmartIndexTheme(darkTheme = false) {
                ReindexDialog(onConfirm = { confirmed++ }, onDismiss = { dismissed++ })
            }
        }
        hasText("Re-index everything?")
        hasText(
            "Smart Index will rebuild its index from scratch. Search keeps working with current results while it " +
                "runs. This can take a few minutes and uses more battery.",
        )
        rule.onNodeWithTag(StatusTestTags.REINDEX_CONFIRM).assertHeightIsAtLeast(48.dp).performClick()
        assertThat(confirmed).isEqualTo(1)
        rule.onNodeWithText("Cancel").assertHeightIsAtLeast(48.dp).performClick()
        assertThat(dismissed).isEqualTo(1)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() {
        this.assertDoesNotExist()
    }
}
