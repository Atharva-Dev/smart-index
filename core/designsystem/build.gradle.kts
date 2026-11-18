// :core:designsystem — M3 theme, Design Spec §5 tokens, shared composables (LLD — App shell §3.3).
plugins {
    alias(libs.plugins.smartindex.android.library)
    alias(libs.plugins.smartindex.android.compose)
}

android {
    namespace = "com.kuriousdragon.smartindex.core.designsystem"
}

dependencies {
    implementation(project(":core:model"))

    // Hosts ComponentActivity for createComposeRule under Robolectric.
    testImplementation(libs.androidx.compose.ui.test.manifest)
}
