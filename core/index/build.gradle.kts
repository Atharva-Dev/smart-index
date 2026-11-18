// :core:index — Room + bundled SQLite FTS5 store (LLD — Index store & search engine).
plugins {
    alias(libs.plugins.smartindex.android.library)
    alias(libs.plugins.smartindex.hilt)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "com.kuriousdragon.smartindex.core.index"
    buildFeatures {
        buildConfig = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite.bundled)
    ksp(libs.androidx.room.compiler)
    implementation("javax.inject:javax.inject:1")

    testImplementation(libs.androidx.sqlite.bundled.jvm)
    testImplementation(libs.androidx.paging.testing)
}

// Opt-in JVM scale benchmark (LLD §7.4): -Psidx.bench=true -Psidx.bench.items=200000
tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
    if (providers.gradleProperty("sidx.bench").orNull == "true") {
        testLogging {
            showStandardStreams = true
        }
    }
    systemProperty("sidx.bench", providers.gradleProperty("sidx.bench").getOrElse("false"))
    systemProperty("sidx.bench.items", providers.gradleProperty("sidx.bench.items").getOrElse("50000"))
    // Gradle must re-run the benchmark when the flags change.
    inputs.property("sidx.bench", providers.gradleProperty("sidx.bench").orElse("false"))
    inputs.property("sidx.bench.items", providers.gradleProperty("sidx.bench.items").orElse("50000"))
}
