// :feature:onboarding — Welcome, 5 permission steps, summary (Onboarding & Index status UI LLD).
plugins {
    alias(libs.plugins.smartindex.android.library)
    alias(libs.plugins.smartindex.android.compose)
    alias(libs.plugins.smartindex.hilt)
}

android {
    namespace = "com.kuriousdragon.smartindex.feature.onboarding"
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

    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
