package com.kuriousdragon.smartindex.feature.home.action

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.ItemDetail
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.OpenTarget
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.core.model.SourceKey
import com.kuriousdragon.smartindex.feature.home.R
import com.kuriousdragon.smartindex.feature.home.state.HomeEffect
import com.kuriousdragon.smartindex.feature.home.state.UiText
import com.kuriousdragon.smartindex.feature.home.testing.FakeContactLookup
import com.kuriousdragon.smartindex.feature.home.testing.FakeIndexControl
import com.kuriousdragon.smartindex.feature.home.testing.FakeIndexQuery
import com.kuriousdragon.smartindex.feature.home.testing.FakeItemUriResolver
import com.kuriousdragon.smartindex.feature.home.testing.document
import com.kuriousdragon.smartindex.feature.home.testing.resultItem
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ItemActionsTest {
    private val resolver = FakeItemUriResolver()
    private val index = FakeIndexQuery()
    private val contacts = FakeContactLookup()
    private val control = FakeIndexControl()
    private val actions = ItemActions(resolver, index, contacts, control)

    private val uri = Uri.parse("content://media/external/file/9")
    private val ready = OpenTarget.Ready(uri, "application/pdf", "a.pdf")
    private val pdf = document(9).copy(ext = "pdf", contentUri = uri.toString())

    private fun launch(e: HomeEffect) = e as HomeEffect.Launch

    private fun detail(id: Long, threadId: Long?, numberRaw: String?) = ItemDetail(
        id = id, key = SourceKey(Source.SMS, id), type = ItemType.MESSAGE, title = "t", primaryTs = 0,
        dates = emptyMap(), folder = null, mime = null, ext = null, sizeBytes = null, durationMs = null,
        contentUri = null, numberRaw = numberRaw, contactName = null, direction = null, msgKind = null,
        threadId = threadId, hasAttachment = false, body = null, linkedRecordingId = null, linkedCallId = null,
    )

    @Test
    fun open_ready_plain() = runTest {
        resolver.target = ready
        val e = launch(actions.open(pdf, chooser = false))
        assertThat(e.attempts).hasSize(1)
        val i = e.attempts.single()
        assertThat(i.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(i.data).isEqualTo(uri)
        assertThat(i.type).isEqualTo("application/pdf")
        assertThat(e.noHandler).isEqualTo(UiText.Res(R.string.err_no_app, listOf("pdf")))
        assertThat(resolver.calls).containsExactly(ItemType.DOCUMENT to uri.toString())
    }

    @Test
    fun open_ready_chooser_carriesReadGrant() = runTest {
        resolver.target = ready
        val i = launch(actions.open(pdf, chooser = true)).attempts.single()
        assertThat(i.action).isEqualTo(Intent.ACTION_CHOOSER)
        assertThat(i.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
    }

    @Test
    fun open_noApp_textWithoutExtension() = runTest {
        resolver.target = ready
        for (ext in listOf(null, "", "  ")) {
            val e = launch(actions.open(pdf.copy(ext = ext), chooser = false))
            assertThat(e.noHandler).isEqualTo(UiText.Res(R.string.err_no_app_noext))
        }
    }

    @Test
    fun open_missing_showsGoneAndCatchesUpOnce() = runTest {
        resolver.target = OpenTarget.Missing
        val e = actions.open(pdf, chooser = false)
        assertThat(e).isEqualTo(HomeEffect.ShowMessage(UiText.Res(R.string.err_file_gone), long = true))
        assertThat(control.catchUpNowCalls).isEqualTo(1)
    }

    @Test
    fun open_noAccess_requestsGroupOfType() = runTest {
        resolver.target = OpenTarget.NoAccess
        assertThat(actions.open(pdf, false)).isEqualTo(HomeEffect.RequestPermission(Group.FILES))
        val photo = resultItem(1, ItemType.PHOTO, contentUri = "content://x")
        assertThat(actions.open(photo, false)).isEqualTo(HomeEffect.RequestPermission(Group.PHOTOS_VIDEOS))
        assertThat(control.catchUpNowCalls).isEqualTo(0)
    }

    @Test
    fun open_notAFile_showsGone() = runTest {
        resolver.target = OpenTarget.NotAFile
        val e = actions.open(pdf.copy(contentUri = null), chooser = false)
        assertThat(e).isEqualTo(HomeEffect.ShowMessage(UiText.Res(R.string.err_file_gone), long = true))
        assertThat(control.catchUpNowCalls).isEqualTo(0)
    }

    @Test
    fun openMessage_threadThenNumber() = runTest {
        index.details[5] = detail(5, threadId = 17, numberRaw = "+447700900123")
        val e = launch(actions.openMessage(resultItem(5, ItemType.MESSAGE)))
        assertThat(e.attempts.map { it.data?.scheme }).containsExactly("content", "sms").inOrder()
        assertThat(e.attempts[0].data.toString()).isEqualTo("content://mms-sms/conversations/17")
        assertThat(e.attempts[1].data?.schemeSpecificPart).isEqualTo("+447700900123")
        assertThat(e.noHandler).isEqualTo(UiText.Res(R.string.err_message_open))
    }

    @Test
    fun openMessage_onlyNumber() = runTest {
        index.details[5] = detail(5, threadId = null, numberRaw = "07700900123")
        val e = launch(actions.openMessage(resultItem(5, ItemType.MESSAGE)))
        assertThat(e.attempts.map { it.data?.scheme }).containsExactly("sms")
    }

    @Test
    fun openMessage_onlyThread_blankNumberIgnored() = runTest {
        index.details[5] = detail(5, threadId = 3, numberRaw = " ")
        val e = launch(actions.openMessage(resultItem(5, ItemType.MESSAGE)))
        assertThat(e.attempts.map { it.data?.scheme }).containsExactly("content")
    }

    @Test
    fun openMessage_neither_andMissingDetail() = runTest {
        val msg = UiText.Res(R.string.err_message_open)
        index.details[5] = detail(5, threadId = null, numberRaw = null)
        assertThat(actions.openMessage(resultItem(5, ItemType.MESSAGE)))
            .isEqualTo(HomeEffect.ShowMessage(msg, long = true))
        assertThat(actions.openMessage(resultItem(6, ItemType.MESSAGE)))
            .isEqualTo(HomeEffect.ShowMessage(msg, long = true))
    }

    @Test
    fun callBack_dialsNumber_orNull() {
        val call = resultItem(2, ItemType.CALL, number = "07700900123")
        val i = launch(actions.callBack(call)!!).attempts.single()
        assertThat(i.action).isEqualTo(Intent.ACTION_DIAL)
        assertThat(i.data?.schemeSpecificPart).isEqualTo("07700900123")
        assertThat(actions.callBack(call.copy(number = null))).isNull()
        assertThat(actions.callBack(call.copy(number = " "))).isNull()
    }

    @Test
    fun openContact_hit_opensLookupUri() = runTest {
        val lookup = Uri.parse("content://com.android.contacts/contacts/lookup/k/1")
        contacts.result = lookup
        val call = resultItem(2, ItemType.CALL, number = "07700900123", contactName = "Sam")
        val i = launch(actions.openContact(call)).attempts.single()
        assertThat(i.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(i.data).isEqualTo(lookup)
        assertThat(contacts.numbers).containsExactly("07700900123")
    }

    @Test
    fun openContact_miss_fallsBackToAddContact() = runTest {
        contacts.result = null
        val call = resultItem(2, ItemType.CALL, number = "07700900123", contactName = "Sam")
        val i = launch(actions.openContact(call)).attempts.single()
        assertThat(i.action).isEqualTo(Intent.ACTION_INSERT_OR_EDIT)
    }

    @Test
    fun openContact_unknownContact_withoutNumber_showsMessage() = runTest {
        val call = resultItem(2, ItemType.CALL, number = null)
        assertThat(actions.openContact(call))
            .isEqualTo(HomeEffect.ShowMessage(UiText.Res(R.string.err_contact_open), long = true))
        assertThat(contacts.numbers).isEmpty()
    }

    @Test
    fun addContact_launchesInsertOrEdit_orNull() {
        val call = resultItem(2, ItemType.CALL, number = "07700900123")
        val i = launch(actions.addContact(call)!!).attempts.single()
        assertThat(i.action).isEqualTo(Intent.ACTION_INSERT_OR_EDIT)
        assertThat(actions.addContact(call.copy(number = null))).isNull()
    }
}
