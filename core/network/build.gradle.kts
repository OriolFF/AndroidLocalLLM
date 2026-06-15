plugins {
    id("llmlocal.android.library")
    id("llmlocal.android.koin")
}

android {
    namespace = "com.llmlocal.core.network"
}

dependencies {
    api(libs.okhttp)
    implementation(libs.okhttp.logging)
}
