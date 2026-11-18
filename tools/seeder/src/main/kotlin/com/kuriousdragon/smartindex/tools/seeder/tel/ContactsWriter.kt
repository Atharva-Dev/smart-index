package com.kuriousdragon.smartindex.tools.seeder.tel

import android.content.ContentProviderOperation
import android.content.ContentProviderResult
import android.content.ContentResolver
import android.provider.ContactsContract
import com.kuriousdragon.smartindex.tools.seeder.SeedLog
import com.kuriousdragon.smartindex.tools.seeder.gen.SeedContact
import com.kuriousdragon.smartindex.tools.seeder.wipe.Ledger
import kotlin.coroutines.cancellation.CancellationException

/** Writes contacts with `applyBatch` (≤ 400 ops per batch); a failing batch is retried contact by contact. */
class ContactsWriter(private val resolver: ContentResolver) {
    suspend fun write(contacts: List<SeedContact>, ledger: Ledger, progress: (Int) -> Unit): WriteStats {
        var written = 0
        var failed = 0
        var done = 0
        for (batch in ContactsBatch.build(contacts)) {
            val results = try {
                resolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(batch.ops))
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                SeedLog.error("CONTACTS SecurityException")
                return WriteStats(written, failed, stopped = true)
            } catch (e: Exception) {
                SeedLog.warn("contacts batch ${e.javaClass.simpleName}")
                null
            }
            if (results != null) {
                written += record(batch, results, ledger)
                failed += batch.contacts.size - recordedCount(batch, results)
            } else {
                // Fall back to one batch per contact so a single bad row costs one contact, not 400 ops.
                for (c in batch.contacts) {
                    val single = ContactsBatch.build(listOf(c)).single()
                    val r = try {
                        resolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(single.ops))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: SecurityException) {
                        SeedLog.error("CONTACTS SecurityException")
                        return WriteStats(written, failed, stopped = true)
                    } catch (e: Exception) {
                        null
                    }
                    if (r != null && recordedCount(single, r) == 1) written += record(single, r, ledger) else failed++
                }
            }
            done += batch.contacts.size
            progress(done)
        }
        return WriteStats(written, failed)
    }

    private fun recordedCount(batch: ContactBatch, results: Array<ContentProviderResult>): Int =
        batch.rawOpIndexes.count { results.getOrNull(it)?.uri != null }

    private fun record(batch: ContactBatch, results: Array<ContentProviderResult>, ledger: Ledger): Int {
        var n = 0
        for (i in batch.rawOpIndexes) {
            val uri = results.getOrNull(i)?.uri ?: continue
            ledger.append("contact", uri.toString())
            n++
        }
        return n
    }
}
