import java.net.URL

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.zircut"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.zircut"
        minSdk = 29
        targetSdk = 34
        versionCode = 241
        versionName = "0.1.241"

        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    // the AI model is read straight from the APK: keep it uncompressed
    androidResources { noCompress += "tflite" }
}

// fix236: on-device AI model, bundled INSIDE the APK. It is fetched ONCE here, at BUILD time (GitHub Actions has internet);
// the installed app never downloads anything. If the file already exists in app/src/main/assets/ai/ it is used as it is.
val aiModelFile = layout.projectDirectory.file("src/main/assets/ai/selfie_segmenter.tflite").asFile
val downloadAiModels by tasks.registering {
    outputs.file(aiModelFile)
    outputs.upToDateWhen { aiModelFile.exists() && aiModelFile.length() > 100_000L }
    doLast {
        aiModelFile.parentFile.mkdirs()
        val url = URL("https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/latest/selfie_segmenter.tflite")
        url.openStream().use { input: java.io.InputStream ->
            aiModelFile.outputStream().use { output: java.io.OutputStream -> input.copyTo(output) }
        }
        check(aiModelFile.length() > 100_000L) { "selfie_segmenter.tflite download failed (file too small). Put the file in app/src/main/assets/ai/ by hand." }
    }
}

// fix239: second model (hair / body / face / clothes). OPTIONAL: if the download fails the build still works and the app simply hides
// the "parts" chips (AiSegmenter.multiAvailable). Put the file in app/src/main/assets/ai/ by hand to make it permanent.
val aiMultiFile = layout.projectDirectory.file("src/main/assets/ai/selfie_multiclass_256x256.tflite").asFile
val downloadAiModelsMulti by tasks.registering {
    outputs.file(aiMultiFile)
    outputs.upToDateWhen { aiMultiFile.exists() && aiMultiFile.length() > 1_000_000L }
    doLast {
        try {
            aiMultiFile.parentFile.mkdirs()
            val url = URL("https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_multiclass_256x256/float32/latest/selfie_multiclass_256x256.tflite")
            url.openStream().use { input: java.io.InputStream ->
                aiMultiFile.outputStream().use { output: java.io.OutputStream -> input.copyTo(output) }
            }
            check(aiMultiFile.length() > 1_000_000L) { "multi-class model too small" }
        } catch (e: Exception) {
            aiMultiFile.delete()
            logger.warn("WARNING: selfie_multiclass_256x256.tflite not downloaded (" + e.message + "). The hair/body parts feature will be hidden in this build.")
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(downloadAiModels, downloadAiModelsMulti) }

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.media3:media3-common:1.4.1")
    implementation("androidx.media3:media3-effect:1.4.1")
    implementation("androidx.media3:media3-transformer:1.4.1")

    // on-device AI (Apache-2.0): person segmentation for background removal / smart mask
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    testImplementation("junit:junit:4.13.2")
}
