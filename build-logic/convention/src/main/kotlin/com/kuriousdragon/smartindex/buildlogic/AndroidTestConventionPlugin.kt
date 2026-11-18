package com.kuriousdragon.smartindex.buildlogic

import com.android.build.api.dsl.TestExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** `smartindex.android.test`: the `:benchmark` Macrobenchmark module (`com.android.test`). */
class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.test")
            extensions.configure<TestExtension> {
                configureAndroidCommon(this)
                defaultConfig.targetSdk = Sdk.TARGET
            }
        }
    }
}
