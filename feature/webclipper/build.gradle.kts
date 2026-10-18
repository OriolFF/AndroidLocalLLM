plugins {
    id("llmlocal.android.feature")
}

android {
    namespace = "com.llmlocal.feature.webclipper"
}

dependencies {
    implementation(project(":core:llm"))
    implementation(project(":core:data"))
    implementation(project(":core:web"))

    implementation(libs.androidx.webkit)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
