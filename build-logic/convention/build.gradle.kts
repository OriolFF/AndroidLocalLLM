plugins {
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// We do not pin a JVM toolchain here so the build works on systems that
// only have JDK 21+ installed. Java 21 is backwards-compatible with
// Java 17 source/target, and AGP 8.7 supports JDK 17+ for the
// Gradle/AGP runtime.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "llmlocal.android.application"
            implementationClass = "com.llmlocal.convention.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "llmlocal.android.library"
            implementationClass = "com.llmlocal.convention.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "llmlocal.android.compose"
            implementationClass = "com.llmlocal.convention.AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "llmlocal.android.feature"
            implementationClass = "com.llmlocal.convention.AndroidFeatureConventionPlugin"
        }
        register("androidKoin") {
            id = "llmlocal.android.koin"
            implementationClass = "com.llmlocal.convention.AndroidKoinConventionPlugin"
        }
        register("androidRoom") {
            id = "llmlocal.android.room"
            implementationClass = "com.llmlocal.convention.AndroidRoomConventionPlugin"
        }
    }
}
