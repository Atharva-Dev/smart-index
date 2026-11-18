package com.kuriousdragon.smartindex.core.permissions.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AllowInSettingsDialogTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var dismissed = 0
    private var opened = 0

    private fun show(group: Group) {
        rule.setContent { AllowInSettingsDialog(group, onDismiss = { dismissed++ }, onOpenSettings = { opened++ }) }
    }

    private fun assertBody(group: Group, body: String) {
        show(group)
        rule.onNodeWithText("Allow in Settings").assertIsDisplayed()
        rule.onNodeWithText(body).assertIsDisplayed()
        rule.onNodeWithText("Cancel").assertIsDisplayed()
        rule.onNodeWithText("Open settings").assertIsDisplayed()
    }

    @Test
    fun `photos and videos text`() = assertBody(
        Group.PHOTOS_VIDEOS,
        "Photos and videos access was turned off. To include photos and videos, " +
            "open Settings › Permissions › Photos and videos and choose Allow.",
    )

    @Test
    fun `messages text`() = assertBody(
        Group.MESSAGES,
        "SMS access was turned off. To include messages, open Settings › Permissions › SMS and choose Allow.",
    )

    @Test
    fun `call history text`() = assertBody(
        Group.CALL_LOG,
        "Call history access was turned off. To include call history, " +
            "open Settings › Permissions › Call logs and choose Allow.",
    )

    @Test
    fun `contacts text`() = assertBody(
        Group.CONTACTS,
        "Contacts access was turned off. To include contacts, open Settings › Permissions › Contacts and choose Allow.",
    )

    @Test
    fun `cancel calls onDismiss`() {
        show(Group.CONTACTS)
        rule.onNodeWithText("Cancel").performClick()
        assertThat(dismissed).isEqualTo(1)
        assertThat(opened).isEqualTo(0)
    }

    @Test
    fun `open settings calls onOpenSettings`() {
        show(Group.CONTACTS)
        rule.onNodeWithText("Open settings").performClick()
        assertThat(opened).isEqualTo(1)
        assertThat(dismissed).isEqualTo(0)
    }

    @Test
    fun `FILES is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { show(Group.FILES) }
    }
}
