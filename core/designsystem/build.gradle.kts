plugins {
    id("llmlocal.android.library")
    id("llmlocal.android.compose")
}

android {
    namespace = "com.llmlocal.core.designsystem"
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material3.window.size)
    api(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.core.ktx)
    // Image accents (color/AccentColors): Celebi quantizer + Score + HCT tones.
    implementation(libs.material.color.utilities)

    testImplementation(libs.junit)
    testImplementation(libs.truth)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
