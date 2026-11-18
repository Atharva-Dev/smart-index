package com.kuriousdragon.smartindex.tools.seeder.tel

import com.kuriousdragon.smartindex.tools.seeder.SeedLog

/** [stopped] = a SecurityException ended the phase early (LLD §5.1). */
data class WriteStats(val written: Int, val failed: Int, val stopped: Boolean = false)

/** Logs `progress <phase> <pct>%` whenever the percentage has advanced by at least [step] (LLD §5.1: every 2 %). */
class ProgressLogger(private val phase: String, private val total: Int, private val step: Int = 2) {
    private var lastLogged = 0

    fun update(done: Int) {
        if (total <= 0) return
        val pct = (done.toLong() * 100 / total).toInt().coerceAtMost(100)
        if (pct - lastLogged >= step || (pct == 100 && lastLogged < 100)) {
            lastLogged = pct
            SeedLog.line("progress $phase $pct%")
        }
    }
}
