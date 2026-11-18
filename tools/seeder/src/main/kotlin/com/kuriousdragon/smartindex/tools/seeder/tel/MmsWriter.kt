package com.kuriousdragon.smartindex.tools.seeder.tel

import android.content.ContentResolver
import android.net.Uri
import android.provider.Telephony
import androidx.core.net.toUri
import com.kuriousdragon.smartindex.tools.seeder.SeedLog
import com.kuriousdragon.smartindex.tools.seeder.gen.SeedMms
import com.kuriousdragon.smartindex.tools.seeder.wipe.Ledger
import kotlin.coroutines.cancellation.CancellationException

/** Inserts MMS messages with SMIL, text, image and address parts. A message whose parts fail is counted failed (its row stays in the ledger so wipe removes it). */
class MmsWriter(
    private val resolver: ContentResolver,
    private val threads: ThreadResolver,
    private val jpeg: () -> ByteArray = { TinyJpeg.bytes },
) {
    suspend fun write(messages: List<SeedMms>, ledger: Ledger, progress: (Int) -> Unit): WriteStats {
        var written = 0
        var failed = 0
        for ((i, m) in messages.withIndex()) {
            try {
                if (writeOne(m, ledger)) written++ else failed++
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                SeedLog.error("MMS SecurityException")
                return WriteStats(written, failed, stopped = true)
            } catch (e: Exception) {
                failed++
            }
            progress(i + 1)
        }
        return WriteStats(written, failed)
    }

    private fun writeOne(m: SeedMms, ledger: Ledger): Boolean {
        val thread = threads.threadId(m.addresses.toSet())
        val pdu = resolver.insert(Telephony.Mms.CONTENT_URI, Rows.mmsPduValues(m, thread)) ?: return false
        val id = pdu.lastPathSegment?.toLongOrNull()
        // Recorded first: if a part insert throws, the wipe still finds the row.
        ledger.append("mms", pdu.toString())
        if (id == null) return false
        val partUri = "content://mms/$id/part".toUri()
        val addrUri = "content://mms/$id/addr".toUri()
        var ok = resolver.insert(partUri, Rows.mmsSmilPart(id)) != null
        m.texts.forEachIndexed { t, text ->
            ok = (resolver.insert(partUri, Rows.mmsTextPart(id, t, text)) != null) && ok
        }
        for (p in 0 until m.imageParts) ok = writeImage(partUri, id, p) && ok
        for (addr in Rows.mmsAddrRows(m, id)) ok = (resolver.insert(addrUri, addr) != null) && ok
        return ok
    }

    private fun writeImage(partUri: Uri, msgId: Long, index: Int): Boolean {
        val uri = resolver.insert(partUri, Rows.mmsImagePart(msgId, index)) ?: return false
        resolver.openOutputStream(uri)?.use { it.write(jpeg()) } ?: return false
        return true
    }
}
