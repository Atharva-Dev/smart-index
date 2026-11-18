package com.kuriousdragon.smartindex.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** `smartindex.android.library`: every `:core:*`, `:source:*`, `:sync` and `:feature:*` module. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                configureAndroidCommon(this)
            }
            addUnitTestDependencies()
        }
    }
}
