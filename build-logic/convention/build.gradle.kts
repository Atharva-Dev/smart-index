plugins {
    `kotlin-dsl`
}

group = "com.kuriousdragon.smartindex.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

tasks.test {
    useJUnit()
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "smartindex.android.application"
            implementationClass = "com.kuriousdragon.smartindex.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "smartindex.android.library"
            implementationClass = "com.kuriousdragon.smartindex.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "smartindex.android.compose"
            implementationClass = "com.kuriousdragon.smartindex.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidTest") {
            id = "smartindex.android.test"
            implementationClass = "com.kuriousdragon.smartindex.buildlogic.AndroidTestConventionPlugin"
        }
        register("root") {
            id = "smartindex.root"
            implementationClass = "com.kuriousdragon.smartindex.buildlogic.RootConventionPlugin"
        }
        register("hilt") {
            id = "smartindex.hilt"
            implementationClass = "com.kuriousdragon.smartindex.buildlogic.HiltConventionPlugin"
        }
    }
}
