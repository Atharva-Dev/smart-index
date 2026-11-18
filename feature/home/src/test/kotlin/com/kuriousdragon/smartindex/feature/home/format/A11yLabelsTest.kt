package com.kuriousdragon.smartindex.feature.home.format

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Direction
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.MsgKind
import com.kuriousdragon.smartindex.feature.home.model.Tab
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class A11yLabelsTest {
    private val res = resources()
    private val env = testEnv()

    private fun label(item: com.kuriousdragon.smartindex.core.model.ResultItem) = A11yLabels.rowLabel(item, env, res)

    @Test
    fun `file row labels`() {
        val photo = item(ItemType.PHOTO, title = "IMG_1.jpg", folder = "DCIM/Camera", sizeBytes = 3L * 1024 * 1024)
        assertThat(label(photo)).isEqualTo("Photo, IMG_1.jpg, Camera, 3.0 MB, 09:15.")
        assertThat(label(item(ItemType.VIDEO, title = "v.mp4", folder = "Camera", sizeBytes = 5 * 1024)))
            .isEqualTo("Video, v.mp4, Camera, 5.0 KB, 09:15.")
        assertThat(label(item(ItemType.DOCUMENT, title = "a.pdf", folder = "Download", sizeBytes = 10)))
            .isEqualTo("Document, a.pdf, Download, 10 B, 09:15.")
        assertThat(label(item(ItemType.OTHER_FILE, title = "a.zip")))
            .isEqualTo("File, a.zip, 09:15.")
    }

    @Test
    fun `row label reads the time in the 12 hour format`() {
        val text = A11yLabels.rowLabel(item(ItemType.OTHER_FILE, title = "a.zip"), testEnv(is24Hour = false), res)
        assertThat(text).isEqualTo("File, a.zip, 9:15 AM.")
    }

    @Test
    fun `call row labels`() {
        val known = item(
            ItemType.CALL,
            title = "+44 1",
            contactName = "Rahul",
            direction = Direction.INCOMING,
            durationMs = 252_000,
            linkedRecordingId = 4,
        )
        assertThat(label(known)).isEqualTo("Incoming call, Rahul, 4 min 12 s, 09:15, has recording.")
        assertThat(label(item(ItemType.CALL, title = "+44 1", direction = Direction.OUTGOING, durationMs = 38_000)))
            .isEqualTo("Outgoing call, +44 1, 38 s, 09:15.")
        assertThat(label(item(ItemType.CALL, title = "+44 1", direction = Direction.MISSED)))
            .isEqualTo("Missed call, +44 1, 09:15.")
        assertThat(label(item(ItemType.CALL, title = "+44 1", direction = Direction.REJECTED)))
            .isEqualTo("Rejected call, +44 1, 09:15.")
        assertThat(label(item(ItemType.CALL, title = "+44 1", direction = Direction.BLOCKED)))
            .isEqualTo("Blocked call, +44 1, 09:15.")
    }

    @Test
    fun `message row labels`() {
        val received = item(
            ItemType.MESSAGE,
            title = "+44 1",
            contactName = "Rahul",
            direction = Direction.RECEIVED,
            msgKind = MsgKind.SMS,
            snippet = "see you at 5",
        )
        assertThat(label(received)).isEqualTo("Received message from Rahul, 09:15: see you at 5")
        assertThat(label(item(ItemType.MESSAGE, title = "+44 1", direction = Direction.SENT, snippet = null)))
            .isEqualTo("Sent message to +44 1, 09:15: no text")
        assertThat(label(item(ItemType.MESSAGE, title = "+44 1", direction = null, snippet = "x")))
            .isEqualTo("Message, +44 1, 09:15: x")
    }

    @Test
    fun `recording row label`() {
        val rec = item(ItemType.RECORDING, title = "c.m4a", contactName = "Rahul", durationMs = 252_000)
        assertThat(label(rec)).isEqualTo("Call recording, Rahul, 4:12, 09:15.")
        assertThat(label(item(ItemType.RECORDING, title = "c.m4a", number = "+44 1")))
            .isEqualTo("Call recording, +44 1, 09:15.")
        assertThat(label(item(ItemType.RECORDING, title = "c.m4a"))).isEqualTo("Call recording, c.m4a, 09:15.")
    }

    @Test
    fun `row actions per type`() {
        assertThat(A11yLabels.actions(item(ItemType.DOCUMENT)))
            .containsExactly(RowAction.OPEN_WITH, RowAction.MORE_ACTIONS).inOrder()
        assertThat(A11yLabels.actions(item(ItemType.RECORDING)))
            .containsExactly(RowAction.PLAY, RowAction.OPEN_WITH_AUDIO).inOrder()
        assertThat(A11yLabels.actions(item(ItemType.MESSAGE))).isEmpty()
        assertThat(A11yLabels.actions(item(ItemType.CALL, number = "1", contactName = null, linkedRecordingId = null)))
            .containsExactly(RowAction.CALL_BACK, RowAction.ADD_CONTACT).inOrder()
        assertThat(A11yLabels.actions(item(ItemType.CALL, number = "1", contactName = "R", linkedRecordingId = 2)))
            .containsExactly(RowAction.CALL_BACK, RowAction.OPEN_CONTACT, RowAction.PLAY_RECORDING).inOrder()
        assertThat(A11yLabels.actions(item(ItemType.CALL, number = null, linkedRecordingId = 2)))
            .containsExactly(RowAction.PLAY_RECORDING)
    }

    @Test
    fun `every action has a label`() {
        for (action in RowAction.entries) assertThat(A11yLabels.actionLabel(action, res)).isNotEmpty()
        assertThat(A11yLabels.actionLabel(RowAction.OPEN_WITH, res)).isEqualTo("Open with")
        assertThat(A11yLabels.actionLabel(RowAction.PLAY_RECORDING, res)).isEqualTo("Play recording")
    }

    @Test
    fun `tab labels follow the display order`() {
        assertThat(Tab.entries.map { A11yLabels.tabLabel(it, res) }).containsExactly(
            "All",
            "Photos",
            "Videos",
            "Documents",
            "Other files",
            "Calls",
            "Recordings",
            "Messages",
        ).inOrder()
    }

    @Test
    fun `tab content descriptions`() {
        assertThat(A11yLabels.tabDescription(Tab.PHOTOS, 18240, false, env, res)).isEqualTo("Photos, 18,240 items")
        assertThat(A11yLabels.tabDescription(Tab.PHOTOS, 1, false, env, res)).isEqualTo("Photos, 1 item")
        assertThat(A11yLabels.tabDescription(Tab.CALLS, 0, false, env, res)).isEqualTo("Calls, 0 items")
        assertThat(A11yLabels.tabDescription(Tab.MESSAGES, 5, true, env, res)).isEqualTo("Messages, permission needed")
        assertThat(A11yLabels.tabDescription(Tab.ALL, null, false, env, res)).isEqualTo("All")
    }

    @Test
    fun `player title and subtitle`() {
        val call =
            item(ItemType.CALL, title = "+44 1", contactName = null, number = "+441", direction = Direction.INCOMING)
        assertThat(PlayerLabels.title(call)).isEqualTo("+44 1")
        assertThat(PlayerLabels.title(call.copy(contactName = "Rahul"))).isEqualTo("Rahul")
        assertThat(PlayerLabels.subtitle(call, env, res)).isEqualTo("Incoming call · Tue 3 Nov, 09:15")
        assertThat(PlayerLabels.subtitle(call.copy(direction = Direction.OUTGOING), env, res))
            .isEqualTo("Outgoing call · Tue 3 Nov, 09:15")
        assertThat(PlayerLabels.subtitle(call.copy(direction = Direction.MISSED), env, res))
            .isEqualTo("Missed call · Tue 3 Nov, 09:15")

        val rec = item(ItemType.RECORDING, title = "c.m4a", number = "+441")
        assertThat(PlayerLabels.title(rec)).isEqualTo("+441")
        assertThat(PlayerLabels.title(rec.copy(number = null))).isEqualTo("c.m4a")
        assertThat(PlayerLabels.title(rec.copy(contactName = "Rahul"))).isEqualTo("Rahul")
        assertThat(PlayerLabels.subtitle(rec, env, res)).isEqualTo("Call recording · Tue 3 Nov, 09:15")
    }
}
