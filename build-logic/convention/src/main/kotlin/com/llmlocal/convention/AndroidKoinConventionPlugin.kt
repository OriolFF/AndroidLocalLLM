package com.llmlocal.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Adds Koin dependencies to a module. This plugin assumes the module is
 * already an Android library or application; it only adds the Koin runtime
 * and the Compose integration.
 */
class AndroidKoinConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val catalog: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
        dependencies {
            add("implementation", catalog.findLibrary("koin-core").get())
            add("implementation", catalog.findLibrary("koin-android").get())
            add("implementation", catalog.findLibrary("kotlinx-coroutines-core").get())
        }
    }
}
