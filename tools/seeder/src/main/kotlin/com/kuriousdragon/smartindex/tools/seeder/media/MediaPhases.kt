package com.kuriousdragon.smartindex.tools.seeder.media

import com.kuriousdragon.smartindex.tools.seeder.PhaseResult
import com.kuriousdragon.smartindex.tools.seeder.SeedContext
import com.kuriousdragon.smartindex.tools.seeder.SeedPhase
import com.kuriousdragon.smartindex.tools.seeder.gen.MediaKind
import com.kuriousdragon.smartindex.tools.seeder.gen.SeedFile
import com.kuriousdragon.smartindex.tools.seeder.gen.SeedPlan
import com.kuriousdragon.smartindex.tools.seeder.tel.ProgressLogger

/** The five file phases in run order: PHOTOS → VIDEOS → DOCUMENTS → OTHERS → RECORDINGS (LLD §5.1). */
object MediaPhases {
    fun build(plan: SeedPlan, writer: MediaStoreWriter): List<SeedPhase> = listOf(
        phase("PHOTOS", plan.files.filter { it.kind == MediaKind.PHOTO }, writer),
        phase("VIDEOS", plan.files.filter { it.kind == MediaKind.VIDEO }, writer),
        phase("DOCUMENTS", plan.files.filter { it.kind == MediaKind.DOCUMENT }, writer),
        phase("OTHERS", plan.files.filter { it.kind == MediaKind.OTHER }, writer),
        phase("RECORDINGS", plan.recordings.map { it.file }, writer),
    )

    private fun phase(name: String, files: List<SeedFile>, writer: MediaStoreWriter) = object : SeedPhase {
        override val name = name

        override suspend fun run(ctx: SeedContext): PhaseResult {
            val progress = ProgressLogger(name, files.size)
            var written = 0
            var failed = 0
            try {
                for ((i, file) in files.withIndex()) {
                    if (writer.write(file) != null) written++ else failed++
                    progress.update(i + 1)
                }
            } finally {
                writer.verifyPending()
                ctx.rescanned = writer.rescanned
                ctx.mtimeFailed = writer.mtimeFailed
            }
            return PhaseResult(written, failed)
        }
    }
}
