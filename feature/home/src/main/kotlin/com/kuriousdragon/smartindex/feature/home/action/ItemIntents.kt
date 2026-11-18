package com.kuriousdragon.smartindex.feature.home.action

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

/** Every outgoing [Intent] (LLD §4.7). Pure; carries no extras beyond the ones listed; logs nothing. */
object ItemIntents {
    /** Open [uri] as [mime] with a one-shot read grant. */
    fun view(uri: Uri, mime: String): Intent =
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    /** "Open with…": the system chooser around [view]. */
    fun chooser(view: Intent): Intent = Intent.createChooser(view, null)

    /** The dialer with [number] filled in. Never ACTION_CALL (no CALL_PHONE). */
    fun dial(number: String): Intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))

    fun openContact(lookupUri: Uri): Intent = Intent(Intent.ACTION_VIEW, lookupUri)

    fun addContact(number: String): Intent =
        Intent(Intent.ACTION_INSERT_OR_EDIT)
            .setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
            .putExtra(ContactsContract.Intents.Insert.PHONE, number)

    fun smsThread(threadId: Long): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("content://mms-sms/conversations/$threadId"))

    fun smsTo(number: String): Intent = Intent(Intent.ACTION_VIEW, Uri.fromParts("sms", number, null))
}
