plugins {
    id("llmlocal.android.feature")
}

android {
    namespace = "com.llmlocal.feature.llmtask"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:llm"))
}
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
