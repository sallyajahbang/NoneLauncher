plugins {
    id("com.android.application")
}

android {
    namespace = "dev.rustcube"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.rustcube"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // Snapdragon 710 hanya arm64-v8a; tidak perlu ABI lain.
        ndk {
            abiFilters += "arm64-v8a"
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
}
