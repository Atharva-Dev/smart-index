// :source:recordings — Samsung recording matcher (LLD SIDX-11).
plugins {
    alias(libs.plugins.smartindex.android.library)
    alias(libs.plugins.smartindex.hilt)
}

android {
    namespace = "com.kuriousdragon.smartindex.source.recordings"
    buildFeatures { buildConfig = true }
    testOptions {
        unitTests.all { it.systemProperty("sidx.testdata", rootProject.file("testdata").absolutePath) }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
}
