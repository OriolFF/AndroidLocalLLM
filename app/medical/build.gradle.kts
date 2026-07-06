plugins {
    id("llmlocal.android.white-label-app")
}

android {
    namespace = "com.llmlocal.medical"
    defaultConfig {
        applicationId = "com.llmlocal.medical"
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