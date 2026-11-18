package com.kuriousdragon.smartindex.feature.home.ui

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.designsystem.theme.SmartIndexTheme
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.feature.home.model.Tab
import com.kuriousdragon.smartindex.feature.home.state.TabAccess
import com.kuriousdragon.smartindex.feature.home.testing.TestEnv
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w1000dp-h800dp")
class TypeTabsTest {
    @get:Rule
    val rule = createComposeRule()

    private val env = TestEnv.env
    private val allOpen: Map<Tab, TabAccess> = Tab.entries.associateWith { TabAccess.Open }
    private val counts = ItemType.entries.associateWith { 0 } + mapOf(
        ItemType.PHOTO to 1234,
        ItemType.VIDEO to 5,
        ItemType.MESSAGE to 1_000_000,
    )

    private fun tabs(
        selected: Tab = Tab.ALL,
        counts: Map<ItemType, Int>? = this.counts,
        access: Map<Tab, TabAccess> = allOpen,
        showProgress: Boolean = false,
        width: Int = 360,
        fontScale: Float = 1f,
        onSelect: (Tab) -> Unit = {},
    ) {
        rule.setContent {
            SmartIndexTheme(darkTheme = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    TypeTabs(
                        selected = selected,
                        counts = counts,
                        tabAccess = access,
                        env = env,
                        showProgress = showProgress,
                        onSelect = onSelect,
                        modifier = Modifier.width(width.dp),
                    )
                }
            }
        }
    }

    private fun description(tab: Tab): String? =
        rule.onNodeWithTag(
            tab.tag,
        ).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.first()

    private fun badgeText(tab: Tab, text: String) = rule.onNodeWithTag(tab.tag).assert(hasText(text))

    @Test
    fun `all 8 labels render in order`() {
        tabs(width = 2000)

        val labels = listOf("All", "Photos", "Videos", "Documents", "Other files", "Calls", "Recordings", "Messages")
        val lefts = Tab.entries.map { rule.onNodeWithTag(it.tag).getUnclippedBoundsInRoot().left }
        assertThat(lefts).isInOrder()
        Tab.entries.zip(labels).forEach { (tab, label) ->
            rule.onNodeWithTag(tab.tag).assert(hasText(label))
        }
    }

    @Test
    fun `badges show grouped counts and ALL is the sum`() {
        tabs(width = 2000)

        badgeText(Tab.PHOTOS, "1,234")
        badgeText(Tab.VIDEOS, "5")
        badgeText(Tab.MESSAGES, "1,000,000")
        badgeText(Tab.CALLS, "0")
        badgeText(Tab.ALL, "1,001,239")
    }

    @Test
    fun `no badge when counts are unknown and the description is the plain label`() {
        tabs(counts = null, width = 2000)

        rule.onNodeWithTag(Tab.PHOTOS.tag).assert(hasText("1,234").not())
        assertThat(description(Tab.PHOTOS)).isEqualTo("Photos")
        assertThat(description(Tab.ALL)).isEqualTo("All")
    }

    @Test
    fun `tab semantics read type and count, singular and plural`() {
        tabs(counts = counts + (ItemType.CALL to 1), width = 2000)

        assertThat(description(Tab.PHOTOS)).isEqualTo("Photos, 1,234 items")
        assertThat(description(Tab.CALLS)).isEqualTo("Calls, 1 item")
    }

    @Test
    fun `a locked tab shows no badge and says permission needed`() {
        val access = allOpen + (Tab.CALLS to TabAccess.Locked(Group.CALL_LOG, GroupState.DENIED))
        tabs(access = access, width = 2000)

        assertThat(description(Tab.CALLS)).isEqualTo("Calls, permission needed")
        rule.onNodeWithTag(Tab.CALLS.tag).assert(hasText("0").not())
        assertThat(description(Tab.PHOTOS)).isEqualTo("Photos, 1,234 items")
    }

    @Test
    fun `selected state follows the selected tab`() {
        tabs(selected = Tab.VIDEOS, width = 2000)

        rule.onNodeWithTag(Tab.VIDEOS.tag).assertIsSelected()
        rule.onNodeWithTag(Tab.PHOTOS.tag).assertIsNotSelected()
        rule.onNodeWithTag(Tab.ALL.tag).assertIsNotSelected()
    }

    @Test
    fun `tapping a tab reports it`() {
        val picked = mutableListOf<Tab>()
        tabs(width = 2000, onSelect = { picked += it })

        rule.onNodeWithTag(Tab.RECORDINGS.tag).performClick()
        assertThat(picked).containsExactly(Tab.RECORDINGS)
    }

    @Test
    fun `selecting the last tab in a narrow row scrolls it into view`() {
        var selected by mutableStateOf(Tab.ALL)
        rule.setContent {
            SmartIndexTheme(darkTheme = false) {
                TypeTabs(selected, counts, allOpen, env, false, {}, Modifier.width(300.dp))
            }
        }
        // The last tab starts off screen.
        assertThat(rule.onNodeWithTag(Tab.MESSAGES.tag).getUnclippedBoundsInRoot().right).isGreaterThan(300.dp)

        selected = Tab.MESSAGES
        rule.waitForIdle()

        rule.onNodeWithTag(Tab.MESSAGES.tag).assertIsDisplayed()
        assertThat(rule.onNodeWithTag(Tab.MESSAGES.tag).getUnclippedBoundsInRoot().right).isAtMost(300.dp)
    }

    @Test
    fun `tags equal Tab tag and every tab is at least 48 dp tall`() {
        tabs(width = 2000)

        Tab.entries.forEach { rule.onNodeWithTag(HomeTestTags.tab(it)).assertHeightIsAtLeast(48.dp) }
    }

    private val progressKey = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    @Test
    fun `progress bar is shown only while showProgress`() {
        tabs(showProgress = true)
        rule.mainClock.autoAdvance = false
        rule.onNode(progressKey).assertIsDisplayed()
        val info = rule.onNode(progressKey).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertThat(info).isEqualTo(ProgressBarRangeInfo.Indeterminate)
    }

    @Test
    fun `no progress bar when showProgress is false`() {
        tabs(showProgress = false)

        rule.onNode(progressKey).assertDoesNotExist()
    }

    @Test
    fun `200 percent font does not clip the tabs`() {
        tabs(width = 2000, fontScale = 2f)

        Tab.entries.forEach { rule.onNodeWithTag(it.tag).assertIsDisplayed().assertHeightIsAtLeast(48.dp) }
        badgeText(Tab.PHOTOS, "1,234")
    }
}
