plugins {
    id("llmlocal.android.white-label-app")
    id("llmlocal.android.room")
}

android {
    namespace = "com.llmlocal.recipekeeper"
    defaultConfig {
        applicationId = "com.llmlocal.recipekeeper"
        versionCode = 1
        versionName = "0.1.0"
        // WS7: the on-device eval runner (src/androidTest/.../EvalRunnerTest)
        // is driven by tools/eval/run_local_model.sh --device.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    // Only this app shows remote / local pictures.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

dependencies {
    // Unit tests (WS5a): parsers, mapper, clip config, sink + a FakeLlmEngine
    // round trip. `org.json` is a stub in android.jar, so the real artifact
    // goes on the test classpath.
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)

    // WS7 eval runner (path b): instrumented test that streams the eval
    // prompts through LiteRtLlmEngine on a device. `litert-lm` is on the
    // androidTest classpath only so the test can build a CPU engine around
    // an explicitly pushed model file (`-e modelPath`); production code
    // keeps getting it transitively through :core:llm.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation(libs.litert.lm)

    // F2: MigrationTestHelper for the v1 → v2 migration test.
    androidTestImplementation(libs.androidx.room.testing)
    // B3: the androidTest classpath is aligned to debugRuntimeClasspath, which
    // resolves kotlinx-serialization 1.7.3; room-testing's generated serializers
    // need >= 1.8 (AbstractMethodError typeParametersSerializers). Pin it on the
    // debug runtime only — release is untouched.
    debugImplementation(libs.kotlinx.serialization.core)
    debugImplementation(libs.kotlinx.serialization.json)

    // X1: Compose UI tests (editor IME, reorder actions, favourite semantics).
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.truth)
    // Compose ui-test drags in espresso 3.5.0, which fails on API 36 (InputManager.getInstance).
    androidTestImplementation(libs.androidx.test.espresso.core)
}
