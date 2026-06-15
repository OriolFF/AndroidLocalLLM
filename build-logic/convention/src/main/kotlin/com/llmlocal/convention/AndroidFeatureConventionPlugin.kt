package com.llmlocal.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Convention plugin for a feature module. Pulls in the typical feature stack:
 * - Compose
 * - Koin (DI)
 * - DesignSystem
 * - Domain
 * - Lifecycle/ViewModel/Compose
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("llmlocal.android.library")
            apply("llmlocal.android.compose")
            apply("llmlocal.android.koin")
        }

        val catalog: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

        dependencies {
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:domain"))
            add("implementation", project(":core:model"))
            add("implementation", project(":core:common"))

            add("implementation", catalog.findLibrary("androidx-core-ktx").get())
            add("implementation", catalog.findLibrary("androidx-lifecycle-runtime-compose").get())
            add("implementation", catalog.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            add("implementation", catalog.findLibrary("androidx-compose-ui-tooling-preview").get())
            add("implementation", catalog.findLibrary("androidx-compose-material3").get())
            add("implementation", catalog.findLibrary("koin-androidx-compose").get())
            add("implementation", catalog.findLibrary("kotlinx-coroutines-android").get())

            add("debugImplementation", catalog.findLibrary("androidx-compose-ui-tooling").get())
            add("debugImplementation", catalog.findLibrary("androidx-compose-ui-test-manifest").get())
        }
    }
}
