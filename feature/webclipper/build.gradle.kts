plugins {
    id("llmlocal.android.feature")
}

android {
    namespace = "com.llmlocal.feature.webclipper"
    defaultConfig {
        // Compose UI tests under src/androidTest (BrowserTopBarTest) need the
        // AndroidX runner; the AGP default runner cannot host them.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // The androidTest APK is self-contained and would otherwise carry every
    // ABI of LiteRT-LM's native code (~46 MB) that no Compose UI test loads.
    // The library AAR itself has no JNI libs, so this only trims the test
    // APK (the shared emulator has ~100 MB free and refuses the full one).
    packaging {
        jniLibs {
            excludes += "**/*.so"
        }
    }
}

dependencies {
    implementation(libs.androidx.datastore.preferences)
    implementation(project(":core:llm"))
    implementation(project(":core:data"))
    implementation(project(":core:web"))

    implementation(libs.androidx.webkit)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented Compose UI tests (the compose BOM platform is added by the
    // compose convention plugin; `ui-test-manifest` ships the host Activity).
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation("androidx.test:runner:1.7.0")
    // ui-test-junit4 pulls espresso-core 3.5, which crashes on API 35+
    // (`InputManager.getInstance` was removed); pin the catalog version.
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.truth)
}
