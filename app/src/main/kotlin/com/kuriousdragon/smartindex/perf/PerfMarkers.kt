package com.kuriousdragon.smartindex.perf

import android.os.SystemClock
import android.util.Log
import com.kuriousdragon.smartindex.core.model.IndexPhase
import com.kuriousdragon.smartindex.core.model.IndexStats
import com.kuriousdragon.smartindex.core.model.IndexStatus
import com.kuriousdragon.smartindex.core.model.IndexStatusSource
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.Mode
import com.kuriousdragon.smartindex.core.model.PerfTrace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Logs index-run transitions for the NFR-004 timing and wraps FULL runs in the `SIDX:index:full` trace
 * section (LLD — Test & benchmark harness §3.2). Only phases, modes, durations and counts are logged
 * (NFR-006 safe).
 */
object PerfMarkers {
    const val TAG = "SiPerf"

    /** How long to wait after IDLE for the stats of the finished run. */
    private const val STATS_WAIT_MS = 5_000L

    /** Collects [source].status and logs transitions; returns the collecting Job. Pure except for [log]/[clock]. */
    fun start(
        source: IndexStatusSource,
        scope: CoroutineScope,
        clock: () -> Long = SystemClock::elapsedRealtime,
        wallClock: () -> Long = System::currentTimeMillis,
        log: (String) -> Unit = { Log.i(TAG, it) },
    ): Job = scope.launch {
        var startMs = -1L
        var lastPhase = IndexPhase.IDLE
        var mode: Mode? = null
        var cookie: Int? = null
        var knownStats: IndexStats? = null

        source.status.collect { s ->
            if (s.phase == IndexPhase.IDLE) {
                if (startMs >= 0) {
                    val durationMs = clock() - startMs
                    val endMode = mode
                    val baseline = knownStats
                    cookie?.let { PerfTrace.end(PerfTrace.INDEX_FULL, it) }
                    startMs = -1L
                    cookie = null
                    mode = null
                    lastPhase = IndexPhase.IDLE
                    // Wait for the finished run's stats off the collector so a new run is not missed.
                    launch {
                        val stats = if (s.stats != null && s.stats != baseline) {
                            s.stats
                        } else {
                            withTimeoutOrNull(STATS_WAIT_MS) {
                                source.status.first { it.stats != null && it.stats != baseline }
                            }?.stats ?: s.stats ?: baseline
                        }
                        log(endLine(endMode, durationMs, stats, s))
                    }
                }
                s.stats?.let { knownStats = it }
                return@collect
            }

            if (startMs < 0) {
                startMs = clock()
                log("run start atMs=${wallClock()}")
            }
            s.stats?.let { knownStats = it }
            s.mode?.let { mode = it }
            if (mode == Mode.FULL && cookie == null) {
                cookie = PerfTrace.nextCookie().also { PerfTrace.begin(PerfTrace.INDEX_FULL, it) }
            }
            if (s.phase != lastPhase) {
                lastPhase = s.phase
                log("phase ${s.phase} mode=${s.mode} sinceStartMs=${clock() - startMs} done=${s.done} total=${s.total}")
            }
        }
    }

    private fun endLine(mode: Mode?, durationMs: Long, stats: IndexStats?, s: IndexStatus): String {
        fun count(type: ItemType) = stats?.countsByType?.get(type) ?: -1
        val counts = ItemType.entries.joinToString(" ") { "${it.name}=${count(it)}" }
        return "run end mode=$mode durationMs=$durationMs total=${stats?.totalItems ?: -1} $counts " +
            "linked=${stats?.recordingsLinked ?: -1} error=${s.error?.kind?.name ?: "none"}"
    }
}
