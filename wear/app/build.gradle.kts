plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.gearan.watch"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gearan.watch"
        // Watch4 Classic ships Wear OS 3 (API 30). minSdk 30 keeps the target tight.
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures { compose = true }
    // Local JVM unit tests: android.util.Log etc. return defaults instead of
    // throwing "not mocked" (GearanLog is called from pure logic under test).
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    // Kotlin 1.9.x era mechanism (compose compiler plugin is Kotlin 2.0+ only).
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.datastore.prefs)
    implementation(libs.security.crypto)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization)
    testImplementation(libs.junit)
    testImplementation(libs.json.test)
}
