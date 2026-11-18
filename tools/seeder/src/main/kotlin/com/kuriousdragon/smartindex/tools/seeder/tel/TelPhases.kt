package com.kuriousdragon.smartindex.tools.seeder.tel

import com.kuriousdragon.smartindex.tools.seeder.PhaseResult
import com.kuriousdragon.smartindex.tools.seeder.SeedContext
import com.kuriousdragon.smartindex.tools.seeder.SeedLog
import com.kuriousdragon.smartindex.tools.seeder.SeedPhase
import com.kuriousdragon.smartindex.tools.seeder.gen.SeedPlan

/** The four telephony phases in run order (CONTACTS → CALLS → SMS → MMS), honouring `only` and `skipSms`. */
object TelPhases {
    fun build(
        plan: SeedPlan,
        contacts: ContactsWriter,
        calls: CallLogWriter,
        sms: SmsWriter,
        mms: MmsWriter,
        isSmsRoleHeld: () -> Boolean,
    ): List<SeedPhase> = listOf(
        phase("CONTACTS", plan.contacts.size) { ctx, progress -> contacts.write(plan.contacts, ctx.ledger, progress) },
        phase("CALLS", plan.calls.size) { ctx, progress -> calls.write(plan.calls, ctx.ledger, progress) },
        smsPhase("SMS", plan.sms.size, isSmsRoleHeld) { ctx, progress -> sms.write(plan.sms, ctx.ledger, progress) },
        smsPhase("MMS", plan.mms.size, isSmsRoleHeld) { ctx, progress -> mms.write(plan.mms, ctx.ledger, progress) },
    )

    private fun phase(
        name: String,
        total: Int,
        body: suspend (SeedContext, (Int) -> Unit) -> WriteStats,
    ) = object : SeedPhase {
        override val name = name

        override suspend fun run(ctx: SeedContext): PhaseResult {
            val stats = body(ctx, ProgressLogger(name, total)::update)
            return PhaseResult(stats.written, stats.failed)
        }
    }

    /** SMS and MMS need the default-SMS role; without it the phase writes nothing and fails every planned row. */
    private fun smsPhase(
        name: String,
        total: Int,
        isRoleHeld: () -> Boolean,
        body: suspend (SeedContext, (Int) -> Unit) -> WriteStats,
    ): SeedPhase {
        val inner = phase(name, total, body)
        return object : SeedPhase {
            override val name = name

            override suspend fun run(ctx: SeedContext): PhaseResult {
                if (total > 0 && !isRoleHeld()) {
                    SeedLog.error("SMS role-not-held")
                    return PhaseResult(0, total)
                }
                return inner.run(ctx)
            }
        }
    }
}
