package com.llmlocal.convention

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Convention plugin that adds Jetpack Compose support to a module.
 * Assumes the module is already an Android library (or application).
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("org.jetbrains.kotlin.plugin.compose")
        }

        val catalog: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
        val bom = catalog.findLibrary("androidx-compose-bom").get()

        val commonExtension = extensions.findByType(
            com.android.build.api.dsl.ApplicationExtension::class.java
        ) as? CommonExtension<*, *, *, *, *, *>
            ?: extensions.findByType(
                com.android.build.gradle.LibraryExtension::class.java
            ) as? CommonExtension<*, *, *, *, *, *>
            ?: error("Compose plugin must be applied after android-application or android-library")

        commonExtension.apply {
            buildFeatures {
                compose = true
            }
        }

        dependencies {
            add("implementation", platform(bom))
            add("androidTestImplementation", platform(bom))
        }
    }
}
