package com.kuriousdragon.smartindex.tools.seeder.wipe

import android.content.ContentResolver
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Telephony
import androidx.core.net.toUri
import com.kuriousdragon.smartindex.tools.seeder.SeedLog
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * `cmd wipe` (LLD §4.10): (1) ledger deletes in chunks, (2) marker deletes for anything left from lost ledgers,
 * (3) delete template caches and ledgers. SMS/MMS only when [smsRoleHeld]; else `warn wipe-sms-skipped`.
 */
class Wiper(
    private val resolver: ContentResolver,
    private val ledgerDir: File,
    private val cacheDir: File,
    private val keepSmsIds: Set<Long>,
    private val smsRoleHeld: Boolean,
    private val ownPackage: String,
) {
    /** Returns the rows deleted per kind. */
    fun wipe(): Map<String, Int> {
        val deleted = linkedMapOf<String, Int>()
        fun add(kind: String, n: Int) {
            if (n > 0) deleted[kind] = (deleted[kind] ?: 0) + n
        }

        if (!smsRoleHeld) SeedLog.warn("wipe-sms-skipped")
        val steps = WipePlan.plan(Ledger.readAllRuns(ledgerDir), keepSmsIds, includeSmsMms = smsRoleHeld)
        for (step in steps) add(step.kind, deleteIds(step))
        for ((kind, op) in markerDeletes()) add(kind, safely(kind, op))

        ledgerDir.listFiles()?.forEach { it.delete() }
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        SeedLog.line("wipe-end deleted=" + deleted.entries.joinToString(",", "{", "}") { "${it.key}=${it.value}" })
        return deleted
    }

    private fun deleteIds(step: WipeStep): Int = safely(step.kind) {
        val where = "_id IN (" + step.ids.joinToString(",") + ")"
        val uri = syncAdapterIfContacts(step.baseUri)
        resolver.delete(uri.toUri(), where, null)
    }

    private fun markerDeletes(): List<Pair<String, () -> Int>> = buildList {
        add("file" to { deleteMediaByOwner() })
        add(
            "call" to
                {
                    resolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls.PHONE_ACCOUNT_ID} = ?", arrayOf(MARKER))
                },
        )
        add(
            "contact" to {
                val uri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                    .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build()
                resolver.delete(uri, "${ContactsContract.RawContacts.SOURCE_ID} LIKE ?", arrayOf("$MARKER:%"))
            },
        )
        if (smsRoleHeld) {
            add("sms" to { deleteSmsByCreator() })
            add("mms" to { resolver.delete(Telephony.Mms.CONTENT_URI, "creator = ?", arrayOf(ownPackage)) })
        }
    }

    private fun deleteMediaByOwner(): Int {
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ?")
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(ownPackage))
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
        }
        return resolver.delete(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), args)
    }

    private fun deleteSmsByCreator(): Int {
        val keep = if (keepSmsIds.isEmpty()) "" else " AND _id NOT IN (" + keepSmsIds.joinToString(",") + ")"
        return resolver.delete(Telephony.Sms.CONTENT_URI, "creator = ?$keep", arrayOf(ownPackage))
    }

    private fun syncAdapterIfContacts(uri: String): String =
        if (uri.startsWith("content://${ContactsContract.AUTHORITY}/")) {
            uri + "?" + ContactsContract.CALLER_IS_SYNCADAPTER + "=true"
        } else {
            uri
        }

    /** A failing delete is logged and counted as 0; it never stops the wipe. */
    private fun safely(kind: String, op: () -> Int): Int = try {
        op()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        SeedLog.error("wipe-$kind ${e.javaClass.simpleName}")
        0
    }

    private companion object {
        const val MARKER = "sidx-seeder"
    }
}
