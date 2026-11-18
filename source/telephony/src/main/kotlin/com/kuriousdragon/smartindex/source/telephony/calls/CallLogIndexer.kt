package com.kuriousdragon.smartindex.source.telephony.calls

import com.kuriousdragon.smartindex.core.model.Group
import com.kuriousdragon.smartindex.core.model.ItemRecord
import com.kuriousdragon.smartindex.core.model.Source
import com.kuriousdragon.smartindex.source.telephony.common.ProviderSourceIndexer
import com.kuriousdragon.smartindex.source.telephony.common.RunStatsBuilder
import com.kuriousdragon.smartindex.source.telephony.common.TelephonyDeps
import javax.inject.Inject
import javax.inject.Singleton

/** Mirrors the system call log into CALL items (FR-004). Cursor = max `CallLog.Calls._ID`. */
@Singleton
class CallLogIndexer @Inject constructor(
    deps: TelephonyDeps,
    private val mapper: CallRowMapper,
) : ProviderSourceIndexer<CallRow>(deps) {
    override val sources = setOf(Source.CALL_LOG)
    override val requires = setOf(Group.CALL_LOG)
    override val source = Source.CALL_LOG
    override val tag = "Tel.Calls"
    override val queries = CallLogQueries
    override val reader = CallRowReader

    override suspend fun map(batch: List<CallRow>, stats: RunStatsBuilder): List<ItemRecord> =
        batch.mapNotNull { mapper.map(it, stats) }
}
