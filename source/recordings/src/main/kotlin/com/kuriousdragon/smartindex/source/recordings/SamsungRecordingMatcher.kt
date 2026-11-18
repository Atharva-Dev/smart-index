package com.kuriousdragon.smartindex.source.recordings

import com.kuriousdragon.smartindex.core.model.PhoneNumberNormalizer
import com.kuriousdragon.smartindex.core.model.RecordingInputs
import com.kuriousdragon.smartindex.core.model.RecordingLink
import com.kuriousdragon.smartindex.core.model.RecordingMatcher
import com.kuriousdragon.smartindex.source.recordings.di.RecordingsDispatcher
import com.kuriousdragon.smartindex.source.recordings.diag.RecordingDiagnostics
import com.kuriousdragon.smartindex.source.recordings.match.IdentityMatcher
import com.kuriousdragon.smartindex.source.recordings.match.MatchEngine
import com.kuriousdragon.smartindex.source.recordings.parse.RecordingNameParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Links Samsung call recordings to call-log items (HLD ADR-010, LLD — Call-recording matcher §5).
 * Pure and deterministic: the same [RecordingInputs] always give the same links, in any input order.
 * Never throws for bad data; rethrows cancellation.
 */
@Singleton
class SamsungRecordingMatcher
@Inject
constructor(
    numbers: PhoneNumberNormalizer,
    parser: RecordingNameParser,
    private val zoneProvider: ZoneIdProvider,
    diagnostics: RecordingDiagnostics,
    @RecordingsDispatcher private val dispatcher: CoroutineDispatcher,
) : RecordingMatcher {
    private val engine = MatchEngine(parser, IdentityMatcher(numbers), diagnostics)

    override suspend fun match(inputs: RecordingInputs): List<RecordingLink> =
        withContext(dispatcher) { engine.run(inputs, zoneProvider.zone()).links }
}
