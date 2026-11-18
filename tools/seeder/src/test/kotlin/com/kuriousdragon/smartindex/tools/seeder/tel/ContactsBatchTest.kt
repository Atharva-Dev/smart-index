package com.kuriousdragon.smartindex.tools.seeder.tel

import android.content.ContentProviderResult
import android.net.Uri
import android.provider.ContactsContract
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.tools.seeder.gen.SeedContact
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContactsBatchTest {
    private fun contacts(
        n: Int,
    ) = List(n) { SeedContact(it, "Name $it", List(1 + it % 3) { j -> "+91980000${it}0$j" }) }

    @Test
    fun `no batch exceeds 400 ops and every contact is in exactly one batch`() {
        val all = contacts(500)
        val batches = ContactsBatch.build(all)
        assertThat(batches.size).isGreaterThan(1)
        assertThat(batches.all { it.ops.size <= 400 }).isTrue()
        assertThat(batches.flatMap { it.contacts }).containsExactlyElementsIn(all).inOrder()
        assertThat(batches.sumOf { it.ops.size }).isEqualTo(all.sumOf { ContactsBatch.opCount(it) })
    }

    @Test
    fun `yield is allowed exactly on raw-contact ops`() {
        val batch = ContactsBatch.build(contacts(30)).first()
        batch.ops.forEachIndexed { i, op ->
            val isRaw = i in batch.rawOpIndexes
            assertThat(op.isYieldAllowed).isEqualTo(isRaw)
            assertThat(
                op.uri,
            ).isEqualTo(if (isRaw) ContactsContract.RawContacts.CONTENT_URI else ContactsContract.Data.CONTENT_URI)
        }
    }

    @Test
    fun `data ops back-reference their own raw contact op`() {
        val batch = ContactsBatch.build(contacts(30)).first()
        // Pretend each op returned a distinct URI whose id is its own index.
        val results = Array(batch.ops.size) { ContentProviderResult(Uri.parse("content://x/$it")) }
        val raws = batch.rawOpIndexes
        batch.ops.forEachIndexed { i, op ->
            if (i in raws) return@forEachIndexed
            val owner = raws.last { it < i }
            val resolved = op.resolveValueBackReferences(results, i)!!
            assertThat(resolved.getAsLong(ContactsContract.Data.RAW_CONTACT_ID)).isEqualTo(owner.toLong())
        }
    }

    @Test
    fun `a contact is never split across batches`() {
        for (b in ContactsBatch.build(contacts(500))) {
            assertThat(b.rawOpIndexes.first()).isEqualTo(0)
            assertThat(b.rawOpIndexes).hasSize(b.contacts.size)
        }
    }

    @Test
    fun `raw contact has the source id and no account, rows carry name and mobile numbers`() {
        val c = SeedContact(7, "आरव शर्मा", listOf("98123 45678", "+919800000007"))
        val ops = ContactsBatch.build(listOf(c)).single().ops
        assertThat(ops).hasSize(4)
        val raw = ops[0].resolveValueBackReferences(emptyArray(), 0)!!
        assertThat(raw.getAsString(ContactsContract.RawContacts.SOURCE_ID)).isEqualTo("sidx-seeder:7")
        assertThat(raw.containsKey(ContactsContract.RawContacts.ACCOUNT_TYPE)).isTrue()
        assertThat(raw.get(ContactsContract.RawContacts.ACCOUNT_TYPE)).isNull()
        assertThat(raw.get(ContactsContract.RawContacts.ACCOUNT_NAME)).isNull()
        val results = Array(4) { ContentProviderResult(Uri.parse("content://x/$it")) }
        val name = ops[1].resolveValueBackReferences(results, 1)!!
        assertThat(
            name.getAsString(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME),
        ).isEqualTo("आरव शर्मा")
        val phone = ops[3].resolveValueBackReferences(results, 3)!!
        assertThat(phone.getAsString(ContactsContract.CommonDataKinds.Phone.NUMBER)).isEqualTo("+919800000007")
        assertThat(phone.getAsInteger(ContactsContract.CommonDataKinds.Phone.TYPE))
            .isEqualTo(ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
    }
}
