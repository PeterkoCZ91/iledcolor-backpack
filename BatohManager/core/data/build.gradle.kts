import java.util.Properties

// API keys come from BatohManager/local.properties (git-ignored), a Gradle property, or env.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun secret(name: String): String =
    localProperties.getProperty(name) ?: project.findProperty(name)?.toString() ?: System.getenv(name) ?: ""

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.hiltAndroid)
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.batoh.core.data"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GIPHY_API_KEY", "\"${secret("GIPHY_API_KEY")}\"")
        buildConfigField("String", "KLIPY_API_KEY", "\"${secret("KLIPY_API_KEY")}\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    testImplementation(libs.junit)
    implementation(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(project(":core:network")) // API
    implementation(project(":core:storage")) // DB/File

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Retrofit / Room / Moshi
    implementation(libs.retrofit)
    implementation(libs.moshi.kotlin)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    
    // USB Serial
    implementation(libs.usb.serial)
}
