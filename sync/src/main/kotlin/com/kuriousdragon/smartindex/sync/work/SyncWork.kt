package com.kuriousdragon.smartindex.sync.work

import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Telephony
import com.kuriousdragon.smartindex.core.model.Group

/** Work names, tag and input keys of the orchestration work graph. */
object SyncWork {
    const val SYNC = "sidx.sync"
    const val SYNC_NEXT = "sidx.sync.next"
    const val PERIODIC = "sidx.periodic"
    const val TRIGGER = "sidx.trigger"
    const val TAG = "sidx"
    const val KEY_REASON = "reason"
}

/** Content URIs that wake the trigger work, for the usable groups only (LLD §5.3; Telephony indexer §4.11). */
object TriggerUris {
    fun forGroups(groups: Set<Group>): List<Uri> {
        val uris = LinkedHashSet<Uri>()
        if (Group.PHOTOS_VIDEOS in groups || Group.FILES in groups) {
            uris += MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }
        if (Group.CALL_LOG in groups) uris += CallLog.Calls.CONTENT_URI
        if (Group.MESSAGES in groups) {
            uris += Telephony.Sms.CONTENT_URI
            uris += Telephony.Mms.CONTENT_URI
            uris += Telephony.MmsSms.CONTENT_URI
        }
        if (Group.CONTACTS in groups) uris += ContactsContract.Contacts.CONTENT_URI
        return uris.toList()
    }
}
