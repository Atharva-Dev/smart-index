// :feature:status — Index status screen (Onboarding & Index status UI LLD §2.1).
plugins {
    alias(libs.plugins.smartindex.android.library)
    alias(libs.plugins.smartindex.android.compose)
    alias(libs.plugins.smartindex.hilt)
}

android {
    namespace = "com.kuriousdragon.smartindex.feature.status"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:permissions"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    // Hosts ComponentActivity for createAndroidComposeRule under Robolectric / instrumented tests.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.androidx.compose.ui.test.manifest)
}
