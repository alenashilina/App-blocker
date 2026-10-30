plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.focuslock"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.focuslock"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // A fixed key, so each new build installs over the previous one.
    // (It's only for your own phone; anyone with this repo could sign with it.)
    signingConfigs {
        getByName("debug") {
            storeFile = file("focuslock.keystore")
            storePassword = "focuslock"
            keyAlias = "focuslock"
            keyPassword = "focuslock"
        }
    }

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

// No libraries needed: the app uses only what's built into Android.
