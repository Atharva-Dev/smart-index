// :core:model — shared types and every HLD §6 contract (interfaces only; implementations live in their modules).
plugins {
    alias(libs.plugins.smartindex.android.library)
}

android {
    namespace = "com.kuriousdragon.smartindex.core.model"
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.androidx.paging.common)
    api(libs.androidx.collection)
}
