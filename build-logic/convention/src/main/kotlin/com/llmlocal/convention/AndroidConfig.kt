package com.llmlocal.convention

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Shared constants for the Android build.
 * Bump these in one place when the project is upgraded.
 */
internal object AndroidConfig {
    const val COMPILE_SDK = 34
    const val MIN_SDK = 24
    const val TARGET_SDK = 34
    const val JVM_TARGET = "17"
}

internal fun Project.configureKotlin() {
    val javaVersion = JavaVersion.VERSION_17
    extensions.findByType(org.gradle.api.plugins.JavaPluginExtension::class.java)?.apply {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    tasks.withType(KotlinCompile::class.java).configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-Xjvm-default=all",
            )
        }
    }
}

// Force Kotlin compile tasks to use a specific JVM toolchain compatible with
// the system Java (21). We do not pin to 17 because that would require a
// toolchain that is not present on the developer machine.
@OptIn(ExperimentalStdlibApi::class)
internal fun org.gradle.api.tasks.TaskContainer.useSystemJavaForKotlin() {
    val javaVersion = JavaVersion.VERSION_17
    withType(KotlinCompile::class.java).configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

internal fun Project.libs(): VersionCatalog =
    extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.libraryProvider(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).get()
