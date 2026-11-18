package com.kuriousdragon.smartindex.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.register

/**
 * `smartindex.root`: applied to the root project only. Registers `verifyModuleGraph` (HLD §8.1, ADR-013)
 * and wires it into the root `check`. The edges are read from every subproject's declared project
 * dependencies (main configurations only; test configurations are ignored, ADR-017) once the projects are configured.
 */
class RootConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            require(this == rootProject) { "smartindex.root must be applied to the root project" }
            pluginManager.apply("base")
            val subs = subprojects
            val graph = provider {
                subs.associate { p ->
                    p.path to p.configurations
                        .filterNot { ModuleRules.isTestConfiguration(it.name) }
                        .flatMap { c -> c.dependencies.withType(ProjectDependency::class.java).map { it.path } }
                        .filter { it != p.path }
                        .distinct()
                }
            }
            val verify = tasks.register<VerifyModuleGraphTask>("verifyModuleGraph") {
                group = "verification"
                description = "Fails if a module depends on a module that HLD §8.1 forbids (e.g. feature -> source)."
                edges.set(graph)
                report.set(layout.buildDirectory.file("reports/module-graph.txt"))
            }
            tasks.named("check") { dependsOn(verify) }
        }
    }
}
