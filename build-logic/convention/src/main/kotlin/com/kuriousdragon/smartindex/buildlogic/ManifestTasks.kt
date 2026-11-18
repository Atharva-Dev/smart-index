package com.kuriousdragon.smartindex.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Fails the build when a variant's merged manifest requests a network permission (ADR-009, R7). */
@CacheableTask
abstract class VerifyNoNetworkPermissionsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val variantName: Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val violations = ManifestChecks.networkPermissionViolations(mergedManifest.get().asFile.readText())
        val out = report.get().asFile
        if (violations.isNotEmpty()) {
            out.writeText(violations.joinToString("\n", postfix = "\n"))
            throw GradleException(
                "Privacy check failed for variant '${variantName.get()}' (FR-016, ADR-009):\n" +
                    violations.joinToString("\n") { "  - $it" } +
                    "\nRemove the dependency that adds it, or add " +
                    "<uses-permission android:name=\"…\" tools:node=\"remove\"/> to app/src/main/AndroidManifest.xml.",
            )
        }
        out.writeText("OK: no network permission in ${variantName.get()}\n")
    }
}

/** Fails the build when a variant's merged manifest exports a component or allows backup (HLD §7). */
@CacheableTask
abstract class VerifyManifestHardeningTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val variantName: Property<String>

    @get:Input
    abstract val debuggable: Property<Boolean>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val violations = ManifestChecks.hardeningViolations(
            mergedManifest.get().asFile.readText(),
            debuggable.get(),
        )
        val out = report.get().asFile
        if (violations.isNotEmpty()) {
            out.writeText(violations.joinToString("\n", postfix = "\n"))
            throw GradleException(
                "Manifest hardening failed for variant '${variantName.get()}' (HLD §7 Security/Privacy):\n" +
                    violations.joinToString("\n") { "  - $it" },
            )
        }
        out.writeText("OK: manifest hardening in ${variantName.get()}\n")
    }
}
