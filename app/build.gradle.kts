plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Для CI: ключ и версия приходят из окружения/секретов GitHub Actions.
val keystorePath: String? = System.getenv("MUSIC_KEYSTORE")
val versionNameOverride: String =
    (project.findProperty("versionNameOverride") as? String) ?: "1.0"
val versionCodeOverride: Int =
    (project.findProperty("versionCodeOverride") as? String)?.toIntOrNull() ?: 1

android {
    namespace = "com.example.music"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.music"
        minSdk = 24
        targetSdk = 35
        versionCode = versionCodeOverride
        versionName = versionNameOverride

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("MUSIC_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MUSIC_KEY_ALIAS")
                keyPassword = System.getenv("MUSIC_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (!keystorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Core & Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // UI
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    // Icons, covers, reorder
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.reorderable)

    // Архивы для импорта альбомов (zip — средствами JDK)
    implementation(libs.junrar)
    implementation(libs.commons.compress)
    implementation(libs.xz)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
