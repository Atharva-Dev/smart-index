package com.kuriousdragon.smartindex.tools.seeder.gen

import java.time.LocalDateTime
import java.time.ZoneId

/** Shared, lazily built plans (the full-scale one is expensive; each test class reuses it). */
object TestPlans {
    val ANCHOR: LocalDateTime = LocalDateTime.of(2026, 11, 1, 12, 0)
    val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

    fun build(profile: Profile, seed: Long = 42, only: Set<String>? = null): SeedPlan =
        Generator(profile, seed, ANCHOR, ZONE, only).plan()

    val small: SeedPlan by lazy { build(Profile.SMALL) }
    val edge: SeedPlan by lazy { build(Profile.EDGE) }
    val nfr002: SeedPlan by lazy { build(Profile.NFR002) }
}
