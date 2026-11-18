package com.kuriousdragon.smartindex.source.telephony.sms

import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.ItemRecord
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.source.telephony.common.ProviderSourceIndexer
import com.kuriousdragon.smartindex.source.telephony.common.RunStatsBuilder
import com.kuriousdragon.smartindex.source.telephony.common.TelephonyDeps
import javax.inject.Inject
import javax.inject.Singleton

/** Mirrors the system SMS store into MESSAGE items, one per message (FR-006). Cursor = max `Sms._ID`. */
@Singleton
class SmsIndexer @Inject constructor(
    deps: TelephonyDeps,
    private val mapper: SmsRowMapper,
) : ProviderSourceIndexer<SmsRow>(deps) {
    override val sources = setOf(Source.SMS)
    override val requires = setOf(Group.MESSAGES)
    override val source = Source.SMS
    override val tag = "Tel.Sms"
    override val queries = SmsQueries
    override val reader = SmsRowReader

    override suspend fun map(batch: List<SmsRow>, stats: RunStatsBuilder): List<ItemRecord> =
        batch.mapNotNull { mapper.map(it, stats) }
}
