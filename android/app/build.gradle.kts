plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun env(name: String, fallback: String) =
    System.getenv(name)?.takeIf { it.isNotBlank() } ?: fallback

android {
    namespace = "co.jkdesign.sahibol"
    compileSdk = 35

    defaultConfig {
        applicationId = "co.jkdesign.sahibol"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "SERVER_URL", "\"${env("SAHIBOL_SERVER_URL", "https://YOUR-LINK.onrender.com")}\"")
        buildConfigField("String", "APP_KEY", "\"${env("SAHIBOL_APP_KEY", "change-me")}\"")
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
