// :tools:seeder — debug-only test-data seeder APK (Test & benchmark harness LLD §3.5, SIDX-15). Never released.
plugins {
    alias(libs.plugins.smartindex.android.application)
}

android {
    namespace = "com.kuriousdragon.smartindex.tools.seeder"
    defaultConfig {
        applicationId = "com.kuriousdragon.smartindex.seeder"
        versionCode = 1
        versionName = "1.0.0"
    }
    lint {
        // The seeder only writes media; it never reads a user-visible photo selection (debug-only test tool).
        disable += "SelectedPhotoAccess"
    }
    testOptions {
        unitTests.all { it.systemProperty("sidx.testdata", rootProject.file("testdata").absolutePath) }
    }
}

androidComponents {
    // Debug-only tool: the release variant does not exist.
    beforeVariants(selector().withBuildType("release")) { it.enable = false }
}

dependencies {
    // No project(...) main dependency: nothing depends on this module and it depends on nothing of the app.
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Test-only: the real recording-name parser validates the planned recording names (LLD §7.1).
    testImplementation(project(":source:recordings"))
    // Test-only: reads back the EXIF the splicer writes (ExifSplicerTest).
    testImplementation(libs.androidx.exifinterface)
}
