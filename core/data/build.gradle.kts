plugins {
    id("llmlocal.android.library")
    id("llmlocal.android.koin")
}

android {
    namespace = "com.llmlocal.core.data"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:llm"))
    implementation(project(":core:model"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.koin.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
