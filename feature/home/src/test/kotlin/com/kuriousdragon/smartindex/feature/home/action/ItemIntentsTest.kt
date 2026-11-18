package com.kuriousdragon.smartindex.feature.home.action

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ItemIntentsTest {
    private val uri = Uri.parse("content://media/external/file/42")

    @Test
    fun view_hasActionDataTypeAndReadGrant() {
        val i = ItemIntents.view(uri, "application/pdf")
        assertThat(i.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(i.data).isEqualTo(uri)
        assertThat(i.type).isEqualTo("application/pdf")
        assertThat(i.flags).isEqualTo(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertThat(i.extras).isNull()
    }

    @Test
    fun chooser_wrapsViewAndCarriesGrant() {
        val view = ItemIntents.view(uri, "application/pdf")
        val c = ItemIntents.chooser(view)
        assertThat(c.action).isEqualTo(Intent.ACTION_CHOOSER)
        @Suppress("DEPRECATION")
        val inner = c.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertThat(inner?.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(inner?.data).isEqualTo(uri)
        assertThat(c.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        assertThat(c.getCharSequenceExtra(Intent.EXTRA_TITLE)).isNull()
    }

    @Test
    fun dial_isActionDialWithTelUri() {
        val i = ItemIntents.dial("+44 7700 900123")
        assertThat(i.action).isEqualTo(Intent.ACTION_DIAL)
        assertThat(i.data?.scheme).isEqualTo("tel")
        assertThat(i.data?.schemeSpecificPart).isEqualTo("+44 7700 900123")
        assertThat(i.flags).isEqualTo(0)
        assertThat(i.extras).isNull()
    }

    @Test
    fun openContact_isViewOfLookupUri() {
        val lookup = Uri.parse("content://com.android.contacts/contacts/lookup/abc/7")
        val i = ItemIntents.openContact(lookup)
        assertThat(i.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(i.data).isEqualTo(lookup)
        assertThat(i.extras).isNull()
    }

    @Test
    fun addContact_isInsertOrEditWithPhoneExtra() {
        val i = ItemIntents.addContact("07700900123")
        assertThat(i.action).isEqualTo(Intent.ACTION_INSERT_OR_EDIT)
        assertThat(i.type).isEqualTo(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
        assertThat(i.data).isNull()
        assertThat(i.getStringExtra(ContactsContract.Intents.Insert.PHONE)).isEqualTo("07700900123")
        assertThat(i.extras?.keySet()).containsExactly(ContactsContract.Intents.Insert.PHONE)
    }

    @Test
    fun smsThread_isViewOfConversationUri() {
        val i = ItemIntents.smsThread(17)
        assertThat(i.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(i.data.toString()).isEqualTo("content://mms-sms/conversations/17")
        assertThat(i.extras).isNull()
    }

    @Test
    fun smsTo_isViewOfSmsUri() {
        val i = ItemIntents.smsTo("+447700900123")
        assertThat(i.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(i.data?.scheme).isEqualTo("sms")
        assertThat(i.data?.schemeSpecificPart).isEqualTo("+447700900123")
        assertThat(i.extras).isNull()
    }

    @Test
    fun noIntentUsesNewTaskFlag() {
        val all = listOf(
            ItemIntents.view(uri, "image/jpeg"),
            ItemIntents.dial("1"),
            ItemIntents.addContact("1"),
            ItemIntents.smsThread(1),
            ItemIntents.smsTo("1"),
        )
        all.forEach { assertThat(it.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isEqualTo(0) }
    }
}
