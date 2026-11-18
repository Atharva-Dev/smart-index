package com.kuriousdragon.smartindex.source.telephony.contacts

import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kuriousdragon.smartindex.core.model.DefaultPhoneNumberNormalizer
import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.GroupState
import com.kuriousdragon.smartindex.core.model.IndexWriter
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.source.telephony.TelephonyConfig
import com.kuriousdragon.smartindex.source.telephony.number.TelephonyCountryIsoSource
import com.kuriousdragon.smartindex.source.telephony.testing.FakeClock
import com.kuriousdragon.smartindex.source.telephony.testing.FakeIndexWriter
import com.kuriousdragon.smartindex.source.telephony.testing.FakePermissionService
import com.kuriousdragon.smartindex.source.telephony.testing.FakeTelephonyProviders
import com.kuriousdragon.smartindex.source.telephony.testing.addPhone
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContactsResolverTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val providers = FakeTelephonyProviders()
    private val writer = FakeIndexWriter()
    private val permissions = FakePermissionService(mapOf(Group.CONTACTS to GroupState.GRANTED))
    private val clock = FakeClock()
    private val snapshot = ContactSnapshotStore(context)
    private val country = TelephonyCountryIsoSource(context)
    private val resolver = ContactsResolver(
        providers,
        writer,
        permissions,
        DefaultPhoneNumberNormalizer(country),
        snapshot,
        clock,
    )
    private var verboseBefore = false
    private val localeBefore = java.util.Locale.getDefault()

    @Before
    fun setUp() {
        // No SIM under Robolectric: the country falls back to the default locale (India for these numbers).
        java.util.Locale.setDefault(java.util.Locale("en", "IN"))
        country.refresh()
        snapshot.delete()
        verboseBefore = SiLog.verbose
        SiLog.verbose = true
        ShadowLog.clear()
    }

    @After
    fun tearDown() {
        java.util.Locale.setDefault(localeBefore)
        SiLog.verbose = verboseBefore
        snapshot.delete()
    }

    private fun phoneQueries() = providers.queries.count { it.uri == Phone.CONTENT_URI }

    /** Moves past the 30 s freshness window. */
    private fun expire() = clock.advance(TelephonyConfig.CONTACTS_FRESH_MS + 1)

    @Test
    fun `the map has a key for the normalized number and for the number as typed`() = runTest {
        providers.addPhone(1, "Rahul", number = "098765 43210", normalized = "+919876543210")
        assertThat(resolver.buildMap()).containsExactly("+919876543210", "Rahul")
    }

    @Test
    fun `the same number saved two ways resolves to one key`() = runTest {
        providers.addPhone(1, "Rahul", number = "+91 98765 43210", normalized = "+919876543210")
        providers.addPhone(2, "Rahul Work", number = "098765 43210")
        val map = resolver.buildMap()
        assertThat(map).hasSize(1)
        assertThat(map.getValue("+919876543210")).isEqualTo("Rahul")
    }

    @Test
    fun `blank and null names are skipped and names are trimmed`() = runTest {
        providers.addPhone(1, "   ", number = "+919876543210")
        providers.addPhone(2, null, number = "+919876543211")
        providers.addPhone(3, "  Asha  ", number = "+919876543212")
        assertThat(resolver.buildMap()).containsExactly("+919876543212", "Asha")
    }

    @Test
    fun `short codes and alphanumeric numbers saved in contacts are named too`() = runTest {
        providers.addPhone(1, "HDFC Bank", number = "AX-HDFCBK")
        providers.addPhone(2, "Airtel", number = "121")
        val map = resolver.buildMap()
        assertThat(map.values).containsExactly("HDFC Bank", "Airtel")
    }

    @Test
    fun `super primary beats starred beats the lowest contact id for a shared number`() = runTest {
        providers.addPhone(1, "Low id", number = "+919876543210")
        providers.addPhone(2, "Starred", number = "+919876543210", starred = 1)
        providers.addPhone(3, "Primary", number = "+919876543210", superPrimary = 1)
        assertThat(resolver.buildMap().getValue("+919876543210")).isEqualTo("Primary")

        providers.db.execSQL("DELETE FROM phones WHERE ${Phone.CONTACT_ID} = 3")
        assertThat(resolver.buildMap().getValue("+919876543210")).isEqualTo("Starred")

        providers.db.execSQL("DELETE FROM phones WHERE ${Phone.CONTACT_ID} = 2")
        assertThat(resolver.buildMap().getValue("+919876543210")).isEqualTo("Low id")
    }

    @Test
    fun `the first run pushes the whole map and writes the snapshot`() = runTest {
        providers.addPhone(1, "Rahul", number = "+919876543210")
        providers.addPhone(2, "Asha", number = "+919876543211")
        val outcome = resolver.ensureFresh(full = false)
        assertThat(outcome).isEqualTo(ContactsOutcome.Updated(pushed = 2, removed = 0))
        assertThat(writer.contactNames).containsExactly("+919876543210", "Rahul", "+919876543211", "Asha")
        assertThat(snapshot.read().keys).containsExactly("+919876543210", "+919876543211")
    }

    @Test
    fun `an identical second run is Unchanged and does not touch the writer`() = runTest {
        providers.addPhone(1, "Rahul", number = "+919876543210")
        resolver.ensureFresh(full = false)
        expire()
        writer.setContactNamesCalls.clear()
        assertThat(resolver.ensureFresh(full = false)).isEqualTo(ContactsOutcome.Unchanged)
        assertThat(writer.setContactNamesCalls).isEmpty()
    }

    @Test
    fun `a rename pushes only that key`() = runTest {
        providers.addPhone(1, "Rahul", number = "+919876543210")
        providers.addPhone(2, "Asha", number = "+919876543211")
        resolver.ensureFresh(full = false)
        expire()
        writer.setContactNamesCalls.clear()
        providers.db.execSQL("UPDATE phones SET ${Phone.DISPLAY_NAME} = 'Rahul K' WHERE ${Phone.CONTACT_ID} = 1")

        assertThat(resolver.ensureFresh(full = false)).isEqualTo(ContactsOutcome.Updated(pushed = 1, removed = 0))
        assertThat(writer.setContactNamesCalls).containsExactly(mapOf("+919876543210" to "Rahul K"))
        assertThat(writer.contactNames["+919876543211"]).isEqualTo("Asha")
    }

    @Test
    fun `a deleted contact pushes its key to null`() = runTest {
        providers.addPhone(1, "Rahul", number = "+919876543210")
        providers.addPhone(2, "Asha", number = "+919876543211")
        resolver.ensureFresh(full = false)
        expire()
        writer.setContactNamesCalls.clear()
        providers.db.execSQL("DELETE FROM phones WHERE ${Phone.CONTACT_ID} = 2")

        assertThat(resolver.ensureFresh(full = false)).isEqualTo(ContactsOutcome.Updated(pushed = 0, removed = 1))
        assertThat(writer.setContactNamesCalls).containsExactly(mapOf<String, String?>("+919876543211" to null))
        assertThat(writer.contactNames).containsExactly("+919876543210", "Rahul")
        assertThat(snapshot.read().keys).containsExactly("+919876543210")
    }

    @Test
    fun `full pushes the whole map even when nothing changed`() = runTest {
        providers.addPhone(1, "Rahul", number = "+919876543210")
        providers.addPhone(2, "Asha", number = "+919876543211")
        resolver.ensureFresh(full = false)
        expire()
        writer.setContactNamesCalls.clear()

        assertThat(resolver.ensureFresh(full = true)).isEqualTo(ContactsOutcome.Updated(pushed = 2, removed = 0))
        assertThat(writer.setContactNamesCalls.single()).hasSize(2)
    }

    @Test
    fun `names are pushed in chunks of 200 keys`() = runTest {
        providers.db.beginTransaction()
        try {
            for (i in 1..450) providers.addPhone(i.toLong(), "Name $i", number = "+9198000%05d".format(i))
            providers.db.setTransactionSuccessful()
        } finally {
            providers.db.endTransaction()
        }
        assertThat(resolver.ensureFresh(full = false)).isEqualTo(ContactsOutcome.Updated(pushed = 450, removed = 0))
        assertThat(writer.setContactNamesCalls.map { it.size }).containsExactly(200, 200, 50).inOrder()
    }

    @Test
    fun `without permission and with a snapshot every key is cleared and the file deleted`() = runTest {
        providers.addPhone(1, "Rahul", number = "+919876543210")
        providers.addPhone(2, "Asha", number = "+919876543211")
        resolver.ensureFresh(full = false)
        permissions.set(Group.CONTACTS, GroupState.DENIED)
        expire()
        providers.queries.clear()

        assertThat(resolver.ensureFresh(full = false)).isEqualTo(ContactsOutcome.NoPermission(cleared = 2))
        assertThat(writer.contactNames).isEmpty()
        assertThat(snapshot.read()).isEmpty()
        assertThat(phoneQueries()).isEqualTo(0)
    }

    @Test
    fun `without permission and without a snapshot the writer is not called`() = runTest {
        permissions.set(Group.CONTACTS, GroupState.DENIED)
        assertThat(resolver.ensureFresh(full = true)).isEqualTo(ContactsOutcome.NoPermission(cleared = 0))
        assertThat(writer.setContactNamesCalls).isEmpty()
        assertThat(providers.queries).isEmpty()
    }

    @Test
    fun `a SecurityException gives Failed and leaves the snapshot unchanged`() = runTest {
        providers.addPhone(1, "Rahul", number = "+919876543210")
        resolver.ensureFresh(full = false)
        val before = snapshot.read()
        expire()
        writer.setContactNamesCalls.clear()
        providers.failOn("content://com.android.contacts", SecurityException("no READ_CONTACTS"))

        assertThat(resolver.ensureFresh(full = true)).isEqualTo(ContactsOutcome.Failed("SecurityException"))
        assertThat(snapshot.read()).isEqualTo(before)
        assertThat(writer.setContactNamesCalls).isEmpty()
    }

    @Test
    fun `a null cursor gives Failed`() = runTest {
        providers.returnNullFor("content://com.android.contacts")
        assertThat(resolver.ensureFresh(full = false)).isInstanceOf(ContactsOutcome.Failed::class.java)
        assertThat(snapshot.read()).isEmpty()
    }

    @Test
    fun `a failing store leaves the snapshot untouched so the next run retries`() = runTest {
        providers.addPhone(1, "Rahul", number = "+919876543210")
        val failing = object : IndexWriter by FakeIndexWriter() {
            override suspend fun setContactNames(names: Map<String, String?>) = throw IllegalStateException("boom")
        }
        val resolver = ContactsResolver(
            providers,
            failing,
            permissions,
            DefaultPhoneNumberNormalizer(country),
            snapshot,
            clock,
        )
        assertThat(resolver.ensureFresh(full = false)).isEqualTo(ContactsOutcome.Failed("IllegalStateException"))
        assertThat(snapshot.read()).isEmpty()
    }

    @Test
    fun `three calls within 30 seconds run one Phone query`() = runTest {
        providers.addPhone(1, "Rahul", number = "+919876543210")
        assertThat(resolver.ensureFresh(full = true)).isInstanceOf(ContactsOutcome.Updated::class.java)
        clock.advance(10_000)
        assertThat(resolver.ensureFresh(full = true)).isEqualTo(ContactsOutcome.Fresh)
        clock.advance(10_000)
        assertThat(resolver.ensureFresh(full = false)).isEqualTo(ContactsOutcome.Fresh)
        assertThat(phoneQueries()).isEqualTo(1)

        clock.advance(10_001)
        resolver.ensureFresh(full = false)
        assertThat(phoneQueries()).isEqualTo(2)
    }

    @Test
    fun `a corrupt snapshot file is treated as empty and the whole map is pushed`() = runTest {
        providers.addPhone(1, "Rahul", number = "+919876543210")
        providers.addPhone(2, "Asha", number = "+919876543211")
        resolver.ensureFresh(full = false)
        expire()
        val file = java.io.File(java.io.File(context.noBackupFilesDir, "telephony"), "contacts.snapshot")
        file.writeBytes(byteArrayOf(1, 2, 3))
        writer.setContactNamesCalls.clear()

        assertThat(resolver.ensureFresh(full = false)).isEqualTo(ContactsOutcome.Updated(pushed = 2, removed = 0))
        assertThat(writer.setContactNamesCalls.single()).hasSize(2)
    }

    @Test
    fun `no log line contains a number or a name`() = runTest {
        providers.addPhone(1, "Rahul Secret", number = "+91 98765 43210")
        resolver.ensureFresh(full = true)
        providers.failOn("content://com.android.contacts", SecurityException("+919876543210 Rahul Secret"))
        expire()
        resolver.ensureFresh(full = true)

        val logs = ShadowLog.getLogs().filter { it.tag == "SI.Tel.Contacts" }
        assertThat(logs).isNotEmpty()
        for (line in logs) {
            assertThat(line.msg).doesNotContain("98765")
            assertThat(line.msg).doesNotContain("Rahul")
            assertThat(line.msg).doesNotContain("Secret")
        }
    }
}
