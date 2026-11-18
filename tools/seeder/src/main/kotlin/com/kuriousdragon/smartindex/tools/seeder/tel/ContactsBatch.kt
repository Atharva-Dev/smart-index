package com.kuriousdragon.smartindex.tools.seeder.tel

import android.content.ContentProviderOperation
import android.provider.ContactsContract
import com.kuriousdragon.smartindex.tools.seeder.gen.SeedContact

/** One `applyBatch` call: its [ops] and, per contact, the index of that contact's raw-contact insert in [ops]. */
class ContactBatch(
    val contacts: List<SeedContact>,
    val ops: List<ContentProviderOperation>,
    val rawOpIndexes: List<Int>,
)

/** Pure construction of the contact insert batches (LLD §3.4): ≤ [MAX_OPS] operations, a contact never split. */
object ContactsBatch {
    const val MAX_OPS = 400

    fun build(contacts: List<SeedContact>): List<ContactBatch> {
        val batches = ArrayList<ContactBatch>()
        var cur = ArrayList<SeedContact>()
        var ops = ArrayList<ContentProviderOperation>()
        var raws = ArrayList<Int>()
        for (c in contacts) {
            val size = opCount(c)
            if (ops.isNotEmpty() && ops.size + size > MAX_OPS) {
                batches += ContactBatch(cur, ops, raws)
                cur = ArrayList()
                ops = ArrayList()
                raws = ArrayList()
            }
            raws += ops.size
            cur += c
            ops += contactOps(c, ops.size)
        }
        if (ops.isNotEmpty()) batches += ContactBatch(cur, ops, raws)
        return batches
    }

    /** raw contact + display name + one row per number. */
    fun opCount(c: SeedContact): Int = 2 + c.numbers.size

    private fun contactOps(c: SeedContact, rawIndex: Int): List<ContentProviderOperation> {
        val out = ArrayList<ContentProviderOperation>(opCount(c))
        out += ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
            .withValue(ContactsContract.RawContacts.SOURCE_ID, c.sourceId)
            .withYieldAllowed(true)
            .build()
        out += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
            .withValue(
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
            )
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, c.displayName)
            .build()
        for (n in c.numbers) {
            out += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, n)
                .withValue(
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                )
                .build()
        }
        return out
    }
}
