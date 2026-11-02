import java.net.URI
import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val targetAbis = (findProperty("yaad.abis") as String? ?: "arm64-v8a").split(",")

// Without this file (see the README), builds use the standard debug key.
val signingProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.sameer.yaad"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.sameer.yaad"
        // Android 12: the first version that can run a background job at once with no notification.
        minSdk = 31
        targetSdk = 35
        versionCode = 6
        versionName = "1.5"

        ndk { abiFilters += targetAbis }
        externalNativeBuild {
            cmake { arguments += "-DCMAKE_BUILD_TYPE=Release" }
        }
    }

    signingConfigs {
        if (!signingProperties.isEmpty) {
            create("personal") {
                storeFile = rootProject.file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        val signing = signingConfigs.findByName("personal") ?: signingConfigs.getByName("debug")
        debug { signingConfig = signing }
        release {
            signingConfig = signing
            isMinifyEnabled = false
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // The models are read straight out of the APK, which only works uncompressed.
    androidResources { noCompress += listOf("bin", "gguf") }

    buildFeatures { compose = true }

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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// The models are too big for the repository. Each is pinned to one revision and checked by hash.
val downloadModels by tasks.registering {
    val models = mapOf(
        "ggml-base-q5_1.bin" to Pair(
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-base-q5_1.bin",
            "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
        ),
        "sorter.gguf" to Pair(
            "https://huggingface.co/unsloth/Qwen3-0.6B-GGUF/resolve/50968a4468ef4233ed78cd7c3de230dd1d61a56b/Qwen3-0.6B-Q4_K_M.gguf",
            "ac2d97712095a558e31573f62f466a3f9d93990898b0ec79d7c974c1780d524a",
        ),
    )
    val folder = layout.projectDirectory.dir("src/main/assets/models").asFile
    doLast {
        folder.mkdirs()
        for ((name, source) in models) {
            val (url, expectedHash) = source
            val target = folder.resolve(name)
            if (target.exists()) continue
            logger.lifecycle("Downloading $name")
            val partial = folder.resolve("$name.part")
            URI(url).toURL().openStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
            val digest = MessageDigest.getInstance("SHA-256")
            partial.inputStream().use { input ->
                val buffer = ByteArray(1 shl 20)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            check(hash == expectedHash) { "$name failed its checksum: got $hash" }
            check(partial.renameTo(target)) { "Could not save $name" }
        }
    }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(downloadModels)
}

dependencies {
    implementation(project(":llm"))

    val composeBom = platform("androidx.compose:compose-bom:2025.07.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("androidx.work:work-runtime-ktx:2.10.2")

    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
