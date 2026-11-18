pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "smart-index"

// HLD §8.1 module layout. The dependency direction is enforced by `./gradlew verifyModuleGraph` (part of `check`).
include(":app")
include(":core:model")
include(":core:designsystem")
include(":core:permissions")
include(":core:index")
include(":source:media")
include(":source:telephony")
include(":source:recordings")
include(":sync")
include(":feature:home")
include(":feature:player")
include(":feature:onboarding")
include(":feature:status")
include(":benchmark")
include(":tools:seeder")
