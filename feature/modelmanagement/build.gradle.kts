plugins {
    id("llmlocal.android.feature")
}

android {
    namespace = "com.llmlocal.feature.modelmanagement"
}

dependencies {
    implementation(project(":core:llm"))

    implementation(libs.androidx.work.runtime)
}