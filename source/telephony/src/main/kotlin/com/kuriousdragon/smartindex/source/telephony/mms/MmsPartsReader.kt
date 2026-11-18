package com.kuriousdragon.smartindex.source.telephony.mms

import android.provider.Telephony.Mms
import com.kuriousdragon.smartindex.source.telephony.TelephonyConfig
import com.kuriousdragon.smartindex.source.telephony.common.CursorReader
import com.kuriousdragon.smartindex.source.telephony.common.TelephonyProviders
import java.util.Locale
import javax.inject.Inject

/** Reads the parts of a batch of MMS with one query and assembles text + attachment flag (LLD §4.2 steps 1–4, 6). */
class MmsPartsReader @Inject constructor(private val providers: TelephonyProviders) {

    private class Acc {
        val texts = ArrayList<String>()
        var hasAttachment = false
    }

    /**
     * One query for the whole batch. Messages without parts are absent from the map. The body is the joined
     * texts only (the mapper adds the subject). A null cursor throws, which fails the run as a provider error.
     */
    fun read(messageIds: LongArray): Map<Long, MmsContent> {
        if (messageIds.isEmpty()) return emptyMap()
        val cursor = providers.query(MmsQueries.parts(messageIds))
            ?: throw IllegalStateException("MMS part provider unavailable")
        val acc = LinkedHashMap<Long, Acc>()
        cursor.use { c ->
            val reader = CursorReader(c)
            while (c.moveToNext()) {
                val messageId = reader.long(Mms.Part.MSG_ID) ?: continue
                val entry = acc.getOrPut(messageId) { Acc() }
                val contentType = reader.string(Mms.Part.CONTENT_TYPE)
                    ?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
                val inlineText = reader.string(Mms.Part.TEXT)
                when (contentType) {
                    null -> inlineText.addTo(entry)

                    CT_TEXT -> {
                        val text = inlineText ?: reader.long(Mms.Part._ID)?.let { partId ->
                            providers.openText(MmsQueries.partUri(partId), TelephonyConfig.MMS_TEXT_PART_MAX_BYTES)
                        }
                        text.addTo(entry)
                    }

                    CT_SMIL -> Unit

                    else -> entry.hasAttachment = true
                }
            }
        }
        return acc.mapValues { (_, a) ->
            MmsContent(body = a.texts.takeIf { it.isNotEmpty() }?.joinToString("\n"), hasAttachment = a.hasAttachment)
        }
    }

    // Texts are kept exactly (no trimming); null / blank pieces are dropped.
    private fun String?.addTo(entry: Acc) {
        if (!isNullOrBlank()) entry.texts.add(this)
    }

    private companion object {
        const val CT_TEXT = "text/plain"
        const val CT_SMIL = "application/smil"
    }
}
