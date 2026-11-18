// :benchmark — Macrobenchmark + baseline profile against :app (Test & benchmark harness LLD, SIDX-15).
plugins {
    alias(libs.plugins.smartindex.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.kuriousdragon.smartindex.benchmark"
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    // Only for the PerfTrace section-name constants (the single source of the trace contract).
    implementation(project(":core:model"))
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

// HLD §8 / QA command: `connectedBenchmarkAndroidTest` runs the benchmarkRelease variant (LLD H6).
tasks.register("connectedBenchmarkAndroidTest") {
    dependsOn("connectedBenchmarkReleaseAndroidTest")
}
