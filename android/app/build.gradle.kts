plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mynavisccooter.capture"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mynavisccooter.capture"
        minSdk = 26
        targetSdk = 35
        versionCode = 100 + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0)
        versionName = "0.2.0-review.${System.getenv("GITHUB_RUN_NUMBER") ?: "local"}"
        buildConfigField("String", "REVISION", "\"${System.getenv("GITHUB_SHA") ?: "local"}\"")
    }

    buildFeatures { buildConfig = true }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
