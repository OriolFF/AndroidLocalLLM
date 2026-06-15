plugins {
    id("llmlocal.android.feature")
}

android {
    namespace = "com.llmlocal.feature.recipe"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:llm"))
}
