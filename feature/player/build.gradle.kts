// :feature:player — mini-player, Media3 (Home & Search UI LLD).
plugins {
    alias(libs.plugins.smartindex.android.library)
    alias(libs.plugins.smartindex.android.compose)
    alias(libs.plugins.smartindex.hilt)
}

android {
    namespace = "com.kuriousdragon.smartindex.feature.player"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Hosts ComponentActivity for createComposeRule under Robolectric / instrumented tests.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.androidx.compose.ui.test.manifest)
}
