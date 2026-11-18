// :core:permissions — PermissionService implementation and Compose requester (LLD — App shell §3.2, §3.3).
plugins {
    alias(libs.plugins.smartindex.android.library)
    alias(libs.plugins.smartindex.hilt)
    alias(libs.plugins.smartindex.android.compose)
}

android {
    namespace = "com.kuriousdragon.smartindex.core.permissions"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Hosts ComponentActivity for createAndroidComposeRule under Robolectric.
    testImplementation(libs.androidx.compose.ui.test.manifest)
}
