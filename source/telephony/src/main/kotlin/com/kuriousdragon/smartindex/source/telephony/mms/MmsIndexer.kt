package com.kuriousdragon.smartindex.source.telephony.mms

import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.ItemRecord
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.source.telephony.common.ProviderSourceIndexer
import com.kuriousdragon.smartindex.source.telephony.common.RunStatsBuilder
import com.kuriousdragon.smartindex.source.telephony.common.TelephonyDeps
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors the system MMS store into MESSAGE items, one per message (FR-006). Cursor = max `Mms._ID`.
 * Per batch: one parts query, then participants (LLD §5.3).
 */
@Singleton
class MmsIndexer @Inject constructor(
    deps: TelephonyDeps,
    private val mapper: MmsRowMapper,
    private val parts: MmsPartsReader,
    private val addresses: MmsAddressResolver,
) : ProviderSourceIndexer<MmsRow>(deps) {
    override val sources = setOf(Source.MMS)
    override val requires = setOf(Group.MESSAGES)
    override val source = Source.MMS
    override val tag = "Tel.Mms"
    override val queries = MmsQueries
    override val reader = MmsRowReader

    override fun onRunStart() = addresses.reset()

    override suspend fun map(batch: List<MmsRow>, stats: RunStatsBuilder): List<ItemRecord> {
        val content = parts.read(LongArray(batch.size) { batch[it].id })
        val participants = addresses.resolve(batch, stats)
        return batch.mapNotNull {
            mapper.map(it, content[it.id] ?: MmsContent.EMPTY, participants[it.id] ?: MmsParticipants.UNKNOWN, stats)
        }
    }
}
