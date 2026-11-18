package com.kuriousdragon.smartindex.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * The module dependency rules of HLD §8.1 (ADR-013), enforced by `verifyModuleGraph` (part of `check`):
 * - `:app` may depend on anything;
 * - `:core:model` depends on no module; other `:core:*` modules only on `:core:model`;
 * - `:source:*` only on `:core:model` and `:core:index` (never on each other);
 * - `:sync` on `:core:*` and `:source:*`;
 * - `:feature:*` on `:core:*` and `:feature:player` (never on `:source:*`, `:sync` or `:app`);
 * - `:benchmark` only on `:app` (its Macrobenchmark target, `targetProjectPath`) and `:core:model`;
 * - `:tools:seeder` only on `:core:model` in its shipped (main) configurations; test-only edges to source/core
 *   modules are allowed (ADR-017), so the seeder's JVM tests can reuse `RecordingNameParser`.
 *
 * Only main configurations are checked: configurations recognised by [isTestConfiguration] are ignored.
 */
object ModuleRules {
    fun isAllowed(from: String, to: String): Boolean = when {
        from == to -> true
        from == ":app" -> true
        from == ":core:model" -> false
        from.startsWith(":core:") -> to == ":core:model"
        from.startsWith(":source:") -> to == ":core:model" || to == ":core:index"
        from == ":sync" -> to.startsWith(":core:") || to.startsWith(":source:")
        from.startsWith(":feature:") -> to.startsWith(":core:") || to == ":feature:player"
        from == ":benchmark" -> to == ":app" || to == ":core:model"
        from == ":tools:seeder" -> to == ":core:model"
        else -> false
    }

    /**
     * True for unit-test and instrumented-test configurations (`testImplementation`, `testDebugImplementation`,
     * `androidTestImplementation`, `debugUnitTestRuntimeClasspath`, `kspTest`, `testFixtures…`), which are not
     * part of the shipped graph (ADR-017).
     */
    fun isTestConfiguration(name: String): Boolean = name.startsWith("test") ||
        name.startsWith("androidTest") ||
        Regex("^(ksp|kapt)(Test|AndroidTest)").containsMatchIn(name) ||
        Regex("(?:UnitTest|AndroidTest)[A-Z]").containsMatchIn(name) ||
        name.endsWith("UnitTest") ||
        name.endsWith("AndroidTest")

    /** One message per forbidden edge in [edges] (module path → module paths it depends on). */
    fun violations(edges: Map<String, Collection<String>>): List<String> = edges.toSortedMap().flatMap { (from, tos) ->
        tos.sorted().filterNot { isAllowed(from, it) }.map { "$from must not depend on $it" }
    }
}

/** Checks the project dependency edges collected at configuration time against [ModuleRules]. */
@CacheableTask
abstract class VerifyModuleGraphTask : DefaultTask() {
    @get:Input
    abstract val edges: MapProperty<String, List<String>>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val violations = ModuleRules.violations(edges.get())
        if (violations.isNotEmpty()) {
            report.get().asFile.writeText(violations.joinToString("\n", postfix = "\n"))
            throw GradleException(
                "Module dependency rules violated (HLD §8.1):\n" + violations.joinToString("\n") { "  - $it" },
            )
        }
        report.get().asFile.writeText(
            edges.get().toSortedMap().entries.joinToString("\n", postfix = "\n") { (k, v) -> "$k -> ${v.sorted()}" },
        )
    }
}
