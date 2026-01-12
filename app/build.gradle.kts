plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // alias(libs.plugins.google.services) // Disabled for manual init
}

import java.util.Properties
import java.io.FileInputStream
import java.io.InputStreamReader




val env = Properties()
val envFile = rootProject.file(".env")
if (envFile.exists()) {
    env.load(InputStreamReader(FileInputStream(envFile), "UTF-8"))
}

android {
    namespace = "com.example.findme"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.findme"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "FIREBASE_API_KEY", "\"${env.getProperty("FIREBASE_API_KEY", "")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${env.getProperty("FIREBASE_APP_ID", "")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${env.getProperty("FIREBASE_PROJECT_ID", "")}\"")
        buildConfigField("String", "FIREBASE_DATABASE_URL", "\"${env.getProperty("FIREBASE_DATABASE_URL", "")}\"")
        buildConfigField("String", "FIREBASE_STORAGE_BUCKET", "\"${env.getProperty("FIREBASE_STORAGE_BUCKET", "")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.play.services.location)
    implementation(libs.androidx.appcompat)
    implementation(libs.cardview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth)
}