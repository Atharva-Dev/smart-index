buildscript {
    dependencies {
        // AGP 9 built-in Kotlin compiles with the Kotlin Gradle plugin on this classpath: pin it to the catalog.
        classpath(libs.kotlin.gradlePlugin)
    }
}

plugins {
    alias(libs.plugins.smartindex.root)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.spotless)
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/.gradle/**", "**/.kotlin/**")
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "**/.gradle/**", "**/.kotlin/**")
        ktlint(ktlintVersion)
    }
    format("misc") {
        target("**/*.md", "**/*.xml", "**/*.toml", "**/*.yml", "**/*.pro", ".gitignore", ".editorconfig")
        targetExclude("**/build/**", "**/.gradle/**", "**/.kotlin/**", "**/schemas/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// verifyModuleGraph (HLD §8.1 dependency direction) is registered by smartindex.root and is part of `check`.
tasks.named("check") {
    dependsOn("spotlessCheck")
    dependsOn(gradle.includedBuild("build-logic").task(":convention:test"))
}
