// :feature:home — Home timeline, Search, rows, actions (Home & Search UI LLD).
plugins {
    alias(libs.plugins.smartindex.android.library)
    alias(libs.plugins.smartindex.android.compose)
    alias(libs.plugins.smartindex.hilt)
}

android {
    namespace = "com.kuriousdragon.smartindex.feature.home"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:permissions"))
    implementation(project(":feature:player"))
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.core)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.androidx.paging.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
