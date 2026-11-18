package com.kuriousdragon.smartindex.sync.work

import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Telephony
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.Group
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TriggerUrisTest {
    private val media = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    @Test
    fun `empty set gives no uris`() {
        assertThat(TriggerUris.forGroups(emptySet())).isEmpty()
    }

    @Test
    fun `photos and videos give the media uri`() {
        assertThat(TriggerUris.forGroups(setOf(Group.PHOTOS_VIDEOS))).containsExactly(media)
    }

    @Test
    fun `files give the media uri`() {
        assertThat(TriggerUris.forGroups(setOf(Group.FILES))).containsExactly(media)
    }

    @Test
    fun `both media groups give the media uri once`() {
        assertThat(TriggerUris.forGroups(setOf(Group.PHOTOS_VIDEOS, Group.FILES))).containsExactly(media)
    }

    @Test
    fun `call log gives the calls uri`() {
        assertThat(TriggerUris.forGroups(setOf(Group.CALL_LOG))).containsExactly(CallLog.Calls.CONTENT_URI)
    }

    @Test
    fun `messages give exactly the three sms and mms uris`() {
        assertThat(TriggerUris.forGroups(setOf(Group.MESSAGES))).containsExactly(
            Telephony.Sms.CONTENT_URI,
            Telephony.Mms.CONTENT_URI,
            Telephony.MmsSms.CONTENT_URI,
        ).inOrder()
    }

    @Test
    fun `contacts give the contacts uri`() {
        assertThat(TriggerUris.forGroups(setOf(Group.CONTACTS)))
            .containsExactly(ContactsContract.Contacts.CONTENT_URI)
    }

    @Test
    fun `all groups give the fixed order`() {
        assertThat(TriggerUris.forGroups(Group.entries.toSet())).containsExactly(
            media,
            CallLog.Calls.CONTENT_URI,
            Telephony.Sms.CONTENT_URI,
            Telephony.Mms.CONTENT_URI,
            Telephony.MmsSms.CONTENT_URI,
            ContactsContract.Contacts.CONTENT_URI,
        ).inOrder()
    }
}
