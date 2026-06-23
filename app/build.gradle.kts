plugins {
    id("llmlocal.android.application")
    id("llmlocal.android.compose")
    id("llmlocal.android.koin")
}

android {
    namespace = "com.llmlocal.recipe"
    defaultConfig {
        applicationId = "com.llmlocal.recipe"
        versionCode = 1
        versionName = "0.1.0"
    }
    // Per-ABI APK splits. The LiteRT-LM AAR ships arm64-v8a and x86_64
    // native libraries (≈ 25–30 MB each); bundling them all into a single
    // universal APK pushes the install above the free-space floor of
    // low-storage devices and emulators. With splits enabled, Gradle
    // produces `app-arm64-v8a-debug.apk`, `app-x86_64-debug.apk`, …
    // and a thin `app-universal-debug.apk` containing all of them.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64", "armeabi-v7a", "x86")
            isUniversalApk = true
        }
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
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    implementation(project(":core:llm"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":feature:recipe"))
    implementation(project(":feature:modelmanagement"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.kotlinx.coroutines.android)
}
