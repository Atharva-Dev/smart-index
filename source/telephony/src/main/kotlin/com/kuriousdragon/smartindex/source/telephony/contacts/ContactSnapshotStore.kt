package com.kuriousdragon.smartindex.source.telephony.contacts

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What was last pushed to the store: `number_norm` → FNV-1a 64 of the contact name (LLD §3.4).
 * File `noBackupFilesDir/telephony/contacts.snapshot`, format `int 1, int count, count × (UTF key, long hash)`.
 */
@Singleton
class ContactSnapshotStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val file = AtomicFile(File(File(context.noBackupFilesDir, "telephony"), "contacts.snapshot"))

    /** Missing, corrupt or unknown-format file → empty map. */
    fun read(): Map<String, Long> = try {
        file.openRead().use { raw ->
            DataInputStream(raw.buffered()).use { input ->
                if (input.readInt() != FORMAT) {
                    emptyMap()
                } else {
                    val count = input.readInt()
                    if (count < 0) {
                        emptyMap()
                    } else {
                        val entries = LinkedHashMap<String, Long>(count)
                        repeat(count) { entries[input.readUTF()] = input.readLong() }
                        entries
                    }
                }
            }
        }
    } catch (_: Exception) {
        emptyMap()
    }

    /** Atomic replace. */
    fun write(entries: Map<String, Long>) {
        file.baseFile.parentFile?.mkdirs()
        val out = file.startWrite()
        try {
            val data = DataOutputStream(out.buffered())
            data.writeInt(FORMAT)
            data.writeInt(entries.size)
            for ((key, hash) in entries) {
                data.writeUTF(key)
                data.writeLong(hash)
            }
            data.flush()
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    fun delete() {
        file.delete()
    }

    private companion object {
        const val FORMAT = 1
    }
}
