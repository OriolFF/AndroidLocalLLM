package com.llmlocal.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Optional: adds Room to a module. Included for template completeness; not
 * used in the recipe app MVP but available for persistence features.
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val catalog: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
        // Intentionally a no-op stub for now — Room is a candidate for a
        // future :core:database module. Add the actual wiring when needed.
        @Suppress("UNUSED_VARIABLE")
        val unused = catalog
    }
}
