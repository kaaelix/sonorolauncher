plugins {
    id("com.android.application")
}

android {
    namespace = "id.local.wuwaconnect"
    compileSdk = 36

    defaultConfig {
        applicationId = "id.local.wuwaconnect"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "0.1.7"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildFeatures {
        aidl = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    signingConfigs {
        create("releaseDebugKey") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("releaseDebugKey")
        }
    }
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
