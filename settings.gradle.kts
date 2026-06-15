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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

// Type-safe project accessors — use `projects.core.designsystem` etc.
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "llmlocalAndroid"

include(":app")
include(":core:common")
include(":core:model")
include(":core:designsystem")
include(":core:network")
include(":core:llm")
include(":core:domain")
include(":core:data")
include(":feature:recipe")
