package com.kuriousdragon.smartindex.tools.seeder.tel

import android.content.ContentResolver
import android.provider.Telephony
import com.kuriousdragon.smartindex.tools.seeder.SeedLog
import com.kuriousdragon.smartindex.tools.seeder.gen.SeedSms
import com.kuriousdragon.smartindex.tools.seeder.wipe.Ledger
import kotlin.coroutines.cancellation.CancellationException

/** Resolves (creating if needed) the conversation thread of a set of addresses. */
fun interface ThreadResolver {
    fun threadId(addresses: Set<String>): Long
}

/** One SMS insert per message; the thread id is cached per `threadKey`. Needs the default-SMS role (checked by the phase). */
class SmsWriter(private val resolver: ContentResolver, private val threads: ThreadResolver) {
    suspend fun write(messages: List<SeedSms>, ledger: Ledger, progress: (Int) -> Unit): WriteStats {
        var written = 0
        var failed = 0
        val threadCache = HashMap<String, Long>()
        for ((i, m) in messages.withIndex()) {
            try {
                val thread = threadCache.getOrPut(m.threadKey) { threads.threadId(setOf(m.address)) }
                val uri = resolver.insert(Telephony.Sms.CONTENT_URI, Rows.smsValues(m, thread))
                if (uri != null) {
                    ledger.append("sms", uri.toString())
                    written++
                } else {
                    failed++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                SeedLog.error("SMS SecurityException")
                return WriteStats(written, failed, stopped = true)
            } catch (e: Exception) {
                failed++
            }
            progress(i + 1)
        }
        return WriteStats(written, failed)
    }
}
