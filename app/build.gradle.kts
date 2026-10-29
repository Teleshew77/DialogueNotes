plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.dialoguenotes"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.dialoguenotes"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }
    signingConfigs {
        create("releaseFromEnvironment") {
            val ks = System.getenv("NIGGA_KEYSTORE_FILE")
            if (!ks.isNullOrBlank()) {
                storeFile = file(ks)
                storePassword = System.getenv("NIGGA_STORE_PASSWORD")
                keyAlias = System.getenv("NIGGA_KEY_ALIAS")
                keyPassword = System.getenv("NIGGA_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("releaseFromEnvironment")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
