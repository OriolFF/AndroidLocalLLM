package com.llmlocal.convention

import androidx.room.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Adds Room (with KSP code generation) to a module.
 *
 * - applies `com.google.devtools.ksp` and the `androidx.room` Gradle plugin
 * - adds `room-runtime` as `implementation` and `room-compiler` as `ksp`
 * - exports schemas to `<module>/schemas` so migrations can be tested
 *
 * Apply after an Android plugin (`llmlocal.android.library` or
 * `llmlocal.android.white-label-app`). First real consumer:
 * `:app:recipekeeper`, which owns its own `RecipeKeeperDatabase`.
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("com.google.devtools.ksp")
            apply("androidx.room")
        }

        val catalog: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

        extensions.configure<RoomExtension> {
            schemaDirectory("$projectDir/schemas")
        }

        dependencies {
            add("implementation", catalog.findLibrary("androidx-room-runtime").get())
            add("ksp", catalog.findLibrary("androidx-room-compiler").get())
        }
    }
}
