// :sync — IndexCoordinator, WorkManager workers, status (LLD — Indexing orchestration, SIDX-12).
// No dependency on any :source:* or :core:index module: everything arrives through :core:model and Hilt.
plugins {
    alias(libs.plugins.smartindex.android.library)
    alias(libs.plugins.smartindex.hilt)
}

android {
    namespace = "com.kuriousdragon.smartindex.sync"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.lifecycle.process)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.lifecycle.runtime.testing)
}
