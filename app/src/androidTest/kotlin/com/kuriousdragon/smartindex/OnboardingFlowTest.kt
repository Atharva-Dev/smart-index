package com.kuriousdragon.smartindex

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.kuriousdragon.smartindex.core.model.Group
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Onboarding smoke on a real device (LLD — Onboarding & Index status UI §4.7, §7 "Wiring"): from a cleared app,
 * Welcome → Get started → Skip on all five steps → Summary with five "Skipped" rows → Start indexing → Home, and
 * onboarding stays gone after a relaunch. Needs a freshly installed app (connectedCheck installs one). Tags are the `sidx_*` resource ids (the screens set `testTagsAsResourceId`).
 */
@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {
    private val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun clearApp() {
        // The instrumentation runs inside the app process, so `pm clear` / `am force-stop` would kill this very
        // test ("Process crashed"). A fresh install (what connectedCheck does) is the clean slate; if onboarding is
        // already completed the first require() below fails with a clear message.
        device.pressHome()
    }

    @Test
    fun skipEverything_thenHome_andOnboardingDoesNotReturn() {
        launch()
        require(WELCOME).also { assertNotNull(it) }
        require(GET_STARTED).click()

        repeat(Group.entries.size) { index ->
            val step = Group.entries[index]
            require("sidx_onboarding_step_${step.name}")
            require(SKIP).click()
        }

        require(SUMMARY)
        Group.entries.forEach { group ->
            val row = require("sidx_onboarding_summary_${group.name}")
            assertTrue("Summary row $group is not 'Skipped'", row.showsSkipped())
        }
        require(START).click()

        require(HOME_LIST)
        assertFalse("Onboarding still visible after Start", device.hasObject(By.res(ROOT)))

        device.pressHome()
        launch() // CLEAR_TASK: a brand-new Activity/ViewModel that re-reads the persisted onboarding flag
        require(HOME_LIST)
        assertFalse("Onboarding came back after relaunch", device.hasObject(By.res(ROOT)))
    }

    private fun launch() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val intent =
            requireNotNull(context.packageManager.getLaunchIntentForPackage(PACKAGE)) {
                "No launch intent for $PACKAGE"
            }
        intent.addFlags(
            android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK,
        )
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), LAUNCH_TIMEOUT_MS)
    }

    private fun require(tag: String): UiObject2 =
        device.wait(Until.findObject(By.res(tag)), FIND_TIMEOUT_MS)
            ?: throw AssertionError("Test tag '$tag' not found within $FIND_TIMEOUT_MS ms")

    private fun UiObject2.showsSkipped(): Boolean =
        text?.contains(SKIPPED) == true || findObject(By.textContains(SKIPPED)) != null

    private companion object {
        const val PACKAGE = "com.kuriousdragon.smartindex"
        const val LAUNCH_TIMEOUT_MS = 10_000L
        const val FIND_TIMEOUT_MS = 5_000L
        const val SKIPPED = "Skipped"

        // Onboarding tags: OnboardingTestTags (LLD §4.7). Home list tag: HomeTestTags.LIST (SIDX-87).
        const val ROOT = "sidx_onboarding_root"
        const val WELCOME = "sidx_onboarding_welcome"
        const val GET_STARTED = "sidx_onboarding_get_started"
        const val SKIP = "sidx_onboarding_skip"
        const val SUMMARY = "sidx_onboarding_summary"
        const val START = "sidx_onboarding_start"
        const val HOME_LIST = "sidx_list"
    }
}
