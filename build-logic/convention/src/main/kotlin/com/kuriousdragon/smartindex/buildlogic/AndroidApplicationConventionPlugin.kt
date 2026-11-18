package com.kuriousdragon.smartindex.buildlogic

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

/**
 * `smartindex.android.application`: an APK module (`:app`, `:tools:seeder`).
 *
 * Besides the common Android settings it registers the privacy gates (ADR-009, FR-016, NFR-006) for
 * **every** variant's merged manifest and wires them into `check` and
 * before `package<Variant>` / `bundle<Variant>`, so no APK or AAB is produced without passing them:
 * - `verify<Variant>NoNetworkPermissions` / aggregate `verifyNoNetworkPermissions`
 * - `verify<Variant>ManifestHardening` / aggregate `verifyManifestHardening`
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            extensions.configure<ApplicationExtension> {
                configureAndroidCommon(this)
                defaultConfig.targetSdk = Sdk.TARGET
            }
            addUnitTestDependencies()

            extensions.configure<ApplicationAndroidComponentsExtension> {
                onVariants { variant ->
                    val cap = variant.name.replaceFirstChar { it.uppercase() }
                    tasks.register<VerifyNoNetworkPermissionsTask>("verify${cap}NoNetworkPermissions") {
                        group = "verification"
                        description = "Fails if the ${variant.name} merged manifest requests a network permission."
                        mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                        variantName.set(variant.name)
                        report.set(layout.buildDirectory.file("reports/privacy/${variant.name}-network.txt"))
                    }
                    tasks.register<VerifyManifestHardeningTask>("verify${cap}ManifestHardening") {
                        group = "verification"
                        description =
                            "Fails if the ${variant.name} merged manifest exports components or allows backup."
                        mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                        variantName.set(variant.name)
                        debuggable.set(variant.debuggable)
                        report.set(layout.buildDirectory.file("reports/privacy/${variant.name}-hardening.txt"))
                    }
                    val netTask = tasks.named("verify${cap}NoNetworkPermissions")
                    val hardTask = tasks.named("verify${cap}ManifestHardening")
                    tasks.matching { it.name == "package$cap" || it.name == "bundle$cap" }.configureEach {
                        dependsOn(netTask, hardTask)
                    }
                }
            }
            val network = tasks.register("verifyNoNetworkPermissions") {
                group = "verification"
                description = "Runs the network-permission check on every variant's merged manifest."
                dependsOn(tasks.withType<VerifyNoNetworkPermissionsTask>())
            }
            val hardening = tasks.register("verifyManifestHardening") {
                group = "verification"
                description = "Runs the exported-component and backup check on every variant's merged manifest."
                dependsOn(tasks.withType<VerifyManifestHardeningTask>())
            }
            tasks.named("check") {
                dependsOn(network, hardening)
            }
        }
    }
}
