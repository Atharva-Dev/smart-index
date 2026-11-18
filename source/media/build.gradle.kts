// :source:media — MediaStore indexer and thumbnails (LLD — Media & file indexer, SIDX-8).
plugins {
    alias(libs.plugins.smartindex.android.library)
    alias(libs.plugins.smartindex.hilt)
}

android {
    namespace = "com.kuriousdragon.smartindex.source.media"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.collection)
    implementation(libs.androidx.core.ktx)
    api(libs.coil.core)
}
