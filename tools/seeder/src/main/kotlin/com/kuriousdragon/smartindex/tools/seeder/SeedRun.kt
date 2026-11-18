package com.kuriousdragon.smartindex.tools.seeder

import com.kuriousdragon.smartindex.tools.seeder.gen.SeedManifest
import com.kuriousdragon.smartindex.tools.seeder.gen.SeedPlan
import com.kuriousdragon.smartindex.tools.seeder.wipe.Ledger
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/** What a phase can use; writers (later tickets) append every created URI to [ledger]. */
class SeedContext(
    val command: SeedCommand,
    val ledger: Ledger,
    /** When both are set, [SeedRun] writes the seed manifest (LLD §4.9) with per-phase written/failed/ms. */
    val plan: SeedPlan? = null,
    val manifestFile: File? = null,
) {
    /** Set by the file phases (MediaStoreWriter counters) and written to the manifest. */
    @Volatile var rescanned: Int = 0

    @Volatile var mtimeFailed: Int = 0
}

/** One step of a run (contacts, calls, SMS, MMS, photos, …). Phases arrive with the writer tickets. */
interface SeedPhase {
    val name: String

    /** Returns the number of rows written; a failing row is counted and skipped, never thrown. */
    suspend fun run(ctx: SeedContext): PhaseResult
}

data class PhaseResult(val written: Int, val failed: Int)

/**
 * Phase orchestration, progress logging and timing (LLD §5.1). This ticket supplies the skeleton with an empty
 * phase list; [onRoleReleaseNow] fires after the MMS phase (or at the end if no MMS phase ran) so the SMS role is
 * held as briefly as possible.
 */
class SeedRun(
    private val ctx: SeedContext,
    private val phases: List<SeedPhase>,
    private val onRoleReleaseNow: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun run() {
        val start = clock()
        var released = false
        val written = linkedMapOf<String, Int>()
        val failed = linkedMapOf<String, Int>()
        val phasesMs = linkedMapOf<String, Long>()
        for (phase in phases) {
            val t0 = clock()
            try {
                val r = phase.run(ctx)
                written[phase.name] = r.written
                failed[phase.name] = r.failed
                phasesMs[phase.name] = clock() - t0
                SeedLog.line("phase-end ${phase.name} written=${r.written} failed=${r.failed} ms=${clock() - t0}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SeedLog.error("${phase.name} ${e.javaClass.simpleName}")
                break
            } finally {
                ctx.ledger.flush()
            }
            if (phase.name == MMS_PHASE) {
                onRoleReleaseNow()
                released = true
            }
        }
        if (!released) onRoleReleaseNow()
        ctx.ledger.flush()
        writeManifest(written, failed, phasesMs)
        SeedLog.line(
            "run-end profile=${ctx.command.profile.name.lowercase()} seed=${ctx.command.seed} " +
                "ms=${clock() - start} manifest=files/seed-manifest.json",
        )
    }

    private fun writeManifest(written: Map<String, Int>, failed: Map<String, Int>, phasesMs: Map<String, Long>) {
        val plan = ctx.plan ?: return
        val file = ctx.manifestFile ?: return
        fun byKind(m: Map<String, Number>) = m.entries.mapNotNull { (phase, v) ->
            SeedManifest.PHASES.indexOf(phase).takeIf { it >= 0 }?.let { SeedManifest.KINDS[it] to v }
        }.toMap()
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(
                SeedManifest.build(
                    plan,
                    written = byKind(written).mapValues { it.value.toInt() },
                    failed = byKind(failed).mapValues { it.value.toInt() },
                    phasesMs = phasesMs.filterKeys { it in SeedManifest.PHASES },
                    rescanned = ctx.rescanned,
                    mtimeFailed = ctx.mtimeFailed,
                    sparse = ctx.command.sparse,
                ),
            )
        }.onFailure { SeedLog.error("manifest ${it.javaClass.simpleName}") }
    }

    companion object {
        const val MMS_PHASE = "MMS"
    }
}
