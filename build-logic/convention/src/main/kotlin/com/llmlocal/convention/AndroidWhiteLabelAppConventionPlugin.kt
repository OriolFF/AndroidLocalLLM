package com.llmlocal.convention

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Convention plugin for a white-label `:app:*` module.
 *
 * Composes everything an `:app:*` needs:
 *  - application plugin + SDK / JVM config (delegates to `llmlocal.android.application`)
 *  - Compose plugin
 *  - Koin plugin
 *  - project dependencies on every `:core:*` module + every `:feature:*` module
 *  - runtime libraries (lifecycle, navigation, koin-android-compose, coroutines, work-runtime)
 *  - debug-only Compose tooling (preview + tooling + test-manifest)
 *
 * The consuming module still declares its own `applicationId`, `namespace`,
 * launcher icon, `AndroidManifest.xml`, `Application` class, `MainActivity`,
 * per-ABI splits (only if it needs them), and `proguard-rules.pro`.
 *
 * To create a new app:
 *
 *  1. Copy any existing `:app:*` module's directory.
 *  2. Update `applicationId`, `namespace`, and `app_name` in resources.
 *  3. Replace launcher icons (`ic_launcher_foreground.xml`, etc.).
 *  4. Define a `DesignTokens` impl + an `LlmTaskConfig<T>` + `LlmTaskLabels`
 *     + per-feature labels, then wire them in your `MainActivity`.
 *  5. Build — the convention plugin handles every common dependency.
 */
class AndroidWhiteLabelAppConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("llmlocal.android.application")
            apply("llmlocal.android.compose")
            apply("llmlocal.android.koin")
        }

        val catalog: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

        // Per-ABI splits. The LiteRT-LM AAR ships arm64-v8a and x86_64
        // native libraries (≈ 25–30 MB each); bundling them all into a
        // single universal APK pushes the install above the free-space
        // floor of low-storage devices and emulators. With splits
        // enabled, Gradle produces `app-arm64-v8a-debug.apk`,
        // `app-x86_64-debug.apk`, … and a thin `app-universal-debug.apk`
        // containing all of them.
        extensions.configure<ApplicationExtension> {
            splits {
                abi {
                    isEnable = true
                    reset()
                    include("arm64-v8a", "x86_64", "armeabi-v7a", "x86")
                    isUniversalApk = true
                }
            }
        }

        dependencies {
            // Project deps — every :core:* + :feature:* the template ships.
            add("implementation", project(":core:common"))
            add("implementation", project(":core:model"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:network"))
            add("implementation", project(":core:llm"))
            add("implementation", project(":core:domain"))
            add("implementation", project(":core:data"))
            add("implementation", project(":core:web"))
            add("implementation", project(":feature:llm-task"))
            add("implementation", project(":feature:modelmanagement"))
            add("implementation", project(":feature:webclipper"))

            // Runtime libs.
            add("implementation", catalog.findLibrary("androidx-core-ktx").get())
            add("implementation", catalog.findLibrary("androidx-activity-compose").get())
            add("implementation", catalog.findLibrary("androidx-lifecycle-runtime-ktx").get())
            add("implementation", catalog.findLibrary("androidx-lifecycle-runtime-compose").get())
            add("implementation", catalog.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            add("implementation", catalog.findLibrary("androidx-navigation-compose").get())
            add("implementation", catalog.findLibrary("androidx-work-runtime").get())
            add("implementation", catalog.findLibrary("koin-android").get())
            add("implementation", catalog.findLibrary("koin-androidx-compose").get())
            add("implementation", catalog.findLibrary("kotlinx-coroutines-android").get())

            // Compose preview tooling — most `:app:*` modules define
            // @Preview composables for designer iteration. These are
            // `implementation` (not debugImplementation) so that release
            // builds compile against the same `@Preview` type the source
            // file references. The tooling runtime is small enough that
            // shipping it in release is acceptable for the template.
            add("implementation", catalog.findLibrary("androidx-compose-ui-tooling-preview").get())
            add("debugImplementation", catalog.findLibrary("androidx-compose-ui-tooling").get())
            add("debugImplementation", catalog.findLibrary("androidx-compose-ui-test-manifest").get())
        }
    }
}