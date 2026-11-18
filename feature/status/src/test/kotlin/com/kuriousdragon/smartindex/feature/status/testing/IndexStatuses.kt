package com.kuriousdragon.smartindex.feature.status.testing

import com.kuriousdragon.smartindex.core.model.IndexError
import com.kuriousdragon.smartindex.core.model.IndexPhase
import com.kuriousdragon.smartindex.core.model.IndexStats
import com.kuriousdragon.smartindex.core.model.IndexStatus
import com.kuriousdragon.smartindex.core.model.ItemType
import com.kuriousdragon.smartindex.core.model.Mode

/** Builds an [IndexStatus]; defaults to IDLE. */
fun indexStatus(
    phase: IndexPhase = IndexPhase.IDLE,
    mode: Mode? = null,
    done: Int = 0,
    total: Int = 0,
    stats: IndexStats? = null,
    lastRun: Long? = null,
    error: IndexError? = null,
): IndexStatus = IndexStatus(
    phase = phase,
    mode = mode,
    done = done,
    total = total,
    stats = stats,
    lastRun = lastRun,
    error = error,
)

/** Builds an [IndexStats] with every [ItemType] present (0 unless given). */
fun indexStats(
    counts: Map<ItemType, Int> = emptyMap(),
    recordingsLinked: Int = 0,
): IndexStats {
    val all = ItemType.entries.associateWith { counts[it] ?: 0 }
    return IndexStats(all, recordingsLinked, all.values.sum())
}
