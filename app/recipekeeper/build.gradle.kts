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
}
