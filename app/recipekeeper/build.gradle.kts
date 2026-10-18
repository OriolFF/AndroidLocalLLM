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
}
