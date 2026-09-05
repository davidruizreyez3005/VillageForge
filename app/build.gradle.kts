plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.villageforge"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.villageforge"
        minSdk = 24
        targetSdk = 34
        versionCode = 5
        versionName = "2.0"
    }
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("signing/villageforge.keystore")
            storePassword = "villageforge2024"
            keyAlias = "villageforge"
            keyPassword = "villageforge2024"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            // Same stable signature for every CI build so APKs upgrade-install cleanly.
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation("com.google.android.filament:filament-android:1.51.6")
    implementation("com.google.android.filament:filamat-android:1.51.6")
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
