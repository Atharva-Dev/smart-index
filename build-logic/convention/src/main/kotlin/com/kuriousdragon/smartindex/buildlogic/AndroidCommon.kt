package com.kuriousdragon.smartindex.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/** SDK levels for every module (HLD §4, NFR-001). */
object Sdk {
    const val COMPILE = 36
    const val TARGET = 36
    const val MIN = 33
}

/** The `libs` version catalog of the main build. */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).orElseThrow {
    IllegalArgumentException("No library alias '$alias' in gradle/libs.versions.toml")
}

/**
 * Settings shared by application, library and test modules: SDK levels, Java 17 bytecode (Gradle itself
 * runs on JDK 21; no toolchain download), lint policy and JVM unit-test options.
 */
internal fun Project.configureAndroidCommon(android: CommonExtension) {
    android.compileSdk = Sdk.COMPILE
    android.defaultConfig.minSdk = Sdk.MIN
    android.defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    android.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    android.compileOptions.targetCompatibility = JavaVersion.VERSION_17
    android.lint.apply {
        lintConfig = rootProject.file("config/lint/lint.xml")
        abortOnError = true
        checkReleaseBuilds = true
        // Security and correctness warnings are raised to errors in config/lint/lint.xml.
        warningsAsErrors = false
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
    android.testOptions.unitTests.isIncludeAndroidResources = true
    // android.util.Log etc. return defaults in plain JVM tests (SiLog is safe to call from any unit test).
    android.testOptions.unitTests.isReturnDefaultValues = true
    android.testOptions.unitTests.all { test ->
        test.maxHeapSize = "2g"
        test.systemProperty("robolectric.logging", "stdout")
    }
    android.packaging.resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*")
}

/** JVM unit-test libraries every Android module gets (ADR-014). */
internal fun Project.addUnitTestDependencies() {
    val bundle = libs.findBundle("unit-test").get()
    dependencies {
        add("testImplementation", bundle)
    }
}
