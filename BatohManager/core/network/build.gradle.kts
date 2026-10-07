plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.hiltAndroid)
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.batoh.core.network"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp.logging)
    
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
