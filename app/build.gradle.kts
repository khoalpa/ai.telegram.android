import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "ai.telegram.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "ai.telegram.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val localProperties = Properties().apply {
            val file = rootProject.file("local.properties")
            if (file.exists()) {
                file.inputStream().use(::load)
            }
        }
        val telegramApiId = providers.gradleProperty("telegramApiId").orNull
            ?: localProperties.getProperty("telegramApiId")
            ?: "0"
        val telegramApiHash = providers.gradleProperty("telegramApiHash").orNull
            ?: localProperties.getProperty("telegramApiHash")
            ?: ""
        val escapedTelegramApiHash = telegramApiHash.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("int", "TELEGRAM_API_ID", (telegramApiId.toIntOrNull() ?: 0).toString())
        buildConfigField("String", "TELEGRAM_API_HASH", "\"$escapedTelegramApiHash\"")
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("outsidePlay") {
            dimension = "distribution"
            buildConfigField("boolean", "ENABLE_PLAY_BILLING", "false")
            buildConfigField("boolean", "ENABLE_VIETQR_DONATION", "true")
            versionNameSuffix = "-outside"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
}

kotlin {
    jvmToolchain(21)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation:1.11.2")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.ui:ui:1.11.2")
    implementation("androidx.compose.ui:ui-tooling-preview:1.11.2")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("com.android.billingclient:billing-ktx:9.0.0")
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("io.github.pytgcalls:ntgcalls:2.2.2")
    implementation("io.github.xephosbot:tdlib-kmp-android:1.8.62")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    ksp("androidx.room:room-compiler:2.8.4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260522")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.11.2")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.11.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.11.2")
}
