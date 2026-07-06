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