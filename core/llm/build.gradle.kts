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

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        // The x86_64 Android emulator does not expose the GPU/NPU
        // accelerators LiteRT-LM expects, so the compiled-model executor
        // fails to build. The CPU backend registers successfully on
        // emulators (and on real devices as a slow fallback).
        // Override to "GPU" / "NPU" on hardware that supports it.
        debug {
            buildConfigField("String", "LLM_BACKEND", "\"CPU\"")
        }
        release {
            buildConfigField("String", "LLM_BACKEND", "\"GPU\"")
        }
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:model"))
    api(project(":core:domain"))
    implementation(project(":core:network"))

    implementation(libs.litert.lm)
    implementation(libs.koin.android)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
