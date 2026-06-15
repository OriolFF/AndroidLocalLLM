plugins {
    id("llmlocal.android.library")
    id("llmlocal.android.koin")
}

android {
    namespace = "com.llmlocal.core.llm"

    defaultConfig {
        // LiteRT-LM ships with .so libraries for the major ABIs. The
        // build script lets the linker pick the right one at install time.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:model"))
    implementation(project(":core:network"))

    implementation(libs.litert.lm)
    implementation(libs.koin.android)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
