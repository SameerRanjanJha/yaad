// A module of its own because llama.cpp and whisper.cpp each bring their own copy of ggml,
// and one CMake project can't build both.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val targetAbis = (findProperty("yaad.abis") as String? ?: "arm64-v8a").split(",")

android {
    namespace = "com.sameer.yaad.llm"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        minSdk = 31
        ndk { abiFilters += targetAbis }
        externalNativeBuild {
            cmake { arguments += "-DCMAKE_BUILD_TYPE=Release" }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(21)
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}
