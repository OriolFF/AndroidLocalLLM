// Top-level build file. Each subproject's build file applies convention plugins
// from :build-logic. The root project itself does not need any plugins.

plugins {
    // Apply false to the AGP and Kotlin plugins so subprojects can opt in
    // without inheriting them at the root.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
