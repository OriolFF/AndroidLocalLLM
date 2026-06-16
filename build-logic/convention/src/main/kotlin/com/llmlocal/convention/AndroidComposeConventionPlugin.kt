package com.llmlocal.convention

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Convention plugin that adds Jetpack Compose support to a module.
 * Assumes the module is already an Android library (or application).
 *
 * `buildFeatures { compose = true }` is configured on the typed Android
 * extension, since AGP 8.13 dropped the shared `CommonExtension<...>`
 * supertype for the Compose DSL.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("org.jetbrains.kotlin.plugin.compose")
        }

        val catalog: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
        val bom = catalog.findLibrary("androidx-compose-bom").get()

        val application = extensions.findByType(ApplicationExtension::class.java)
        val library = extensions.findByType(LibraryExtension::class.java)
        check(application != null || library != null) {
            "Compose plugin must be applied after android-application or android-library"
        }

        application?.buildFeatures { compose = true }
        library?.buildFeatures { compose = true }

        dependencies {
            add("implementation", platform(bom))
            add("androidTestImplementation", platform(bom))
        }
    }
}
