// :source:telephony — calls, SMS/MMS, contacts (LLD — Telephony indexer, SIDX-10).
plugins {
    alias(libs.plugins.smartindex.android.library)
    alias(libs.plugins.smartindex.hilt)
}

android {
    namespace = "com.kuriousdragon.smartindex.source.telephony"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.collection)

    // End-to-end telephony -> store test (SIDX-108); test-only edge, allowed by ADR-017.
    testImplementation(project(":core:index"))
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.androidx.sqlite.bundled.jvm)
    testImplementation(libs.androidx.paging.testing)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
