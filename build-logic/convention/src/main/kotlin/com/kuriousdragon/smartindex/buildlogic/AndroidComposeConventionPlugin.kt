package com.kuriousdragon.smartindex.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * `smartindex.android.compose`: apply after `smartindex.android.application` or `.library`. Adds the
 * Compose compiler plugin, the Compose BOM and the common Compose artifacts (Material 3).
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            val android = extensions.getByName("android") as CommonExtension
            android.buildFeatures.compose = true
            dependencies {
                val bom = platform(libs.lib("androidx-compose-bom"))
                add("implementation", bom)
                add("androidTestImplementation", bom)
                add("testImplementation", bom)
                add("implementation", libs.lib("androidx-compose-ui"))
                add("implementation", libs.lib("androidx-compose-ui-graphics"))
                add("implementation", libs.lib("androidx-compose-foundation"))
                add("implementation", libs.lib("androidx-compose-material3"))
                add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
                add("testImplementation", libs.lib("androidx-compose-ui-test-junit4"))
            }
        }
    }
}
