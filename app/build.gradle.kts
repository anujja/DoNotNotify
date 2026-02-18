import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize")
}

// Load keystore properties from local.properties or environment variables
val keystoreProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    keystoreProperties.load(localPropertiesFile.inputStream())
}

fun getKeystoreProperty(key: String): String? {
    return keystoreProperties.getProperty(key) ?: System.getenv(key)
}

android {
    namespace = "com.donotnotify.donotnotify"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.donotnotify.donotnotify"
        minSdk = 24
        targetSdk = 36
        versionCode = 34
        versionName = "3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Public key for GitHub/community releases - credentials are not secret
        create("github") {
            storeFile = file("github-release.jks")
            storePassword = "public-key-not-secret"
            keyAlias = "github-release"
            keyPassword = "public-key-not-secret"
        }
        // Private key for Play Store releases - credentials from local.properties or env vars
        create("playStore") {
            val storeFilePath = getKeystoreProperty("KEYSTORE_FILE")
            if (storeFilePath != null && file(storeFilePath).exists()) {
                storeFile = file(storeFilePath)
                storePassword = getKeystoreProperty("KEYSTORE_PASSWORD")
                keyAlias = getKeystoreProperty("KEY_ALIAS")
                keyPassword = getKeystoreProperty("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use Play Store key if available, otherwise fall back to public GitHub key
            val playStoreConfig = signingConfigs.findByName("playStore")
            signingConfig = if (playStoreConfig?.storeFile != null) {
                playStoreConfig
            } else {
                signingConfigs.getByName("github")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3) // Explicitly added Material3 dependency
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.gson)
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
