package com.kuriousdragon.smartindex.source.telephony.contacts

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.DataOutputStream
import java.io.File
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContactSnapshotStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = ContactSnapshotStore(context)
    private val file = File(File(context.noBackupFilesDir, "telephony"), "contacts.snapshot")

    @Before
    fun setUp() {
        store.delete()
    }

    @Test
    fun `write then read round trips every entry`() {
        val entries = mapOf("+919876543210" to 1L, "AX-HDFC" to -42L, "+14155550100" to Long.MAX_VALUE)
        store.write(entries)
        assertThat(store.read()).isEqualTo(entries)
    }

    @Test
    fun `a missing file reads as empty`() {
        assertThat(store.read()).isEmpty()
    }

    @Test
    fun `an unknown format number reads as empty`() {
        file.parentFile!!.mkdirs()
        DataOutputStream(file.outputStream()).use {
            it.writeInt(2)
            it.writeInt(1)
            it.writeUTF("+919876543210")
            it.writeLong(7L)
        }
        assertThat(store.read()).isEmpty()
    }

    @Test
    fun `a truncated file reads as empty`() {
        file.parentFile!!.mkdirs()
        DataOutputStream(file.outputStream()).use {
            it.writeInt(1)
            it.writeInt(5)
            it.writeUTF("+919876543210")
        }
        assertThat(store.read()).isEmpty()
    }

    @Test
    fun `a second write atomically replaces the first and delete removes the file`() {
        store.write(mapOf("a" to 1L, "b" to 2L))
        store.write(mapOf("c" to 3L))
        assertThat(store.read()).containsExactly("c", 3L)
        store.delete()
        assertThat(store.read()).isEmpty()
        assertThat(file.exists()).isFalse()
    }
}
