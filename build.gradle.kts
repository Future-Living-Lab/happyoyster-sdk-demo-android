plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Keep plugin/SDK versions aligned with the Happy Oyster Android SDK release this demo tracks.
val opensdkVersion = "0.2.3"
val okhttpVersion = "4.12.0"
val coroutinesVersion = "1.9.0"
val kotlinxSerializationVersion = "1.7.3"
val coreKtxVersion = "1.18.0"
val lifecycleRuntimeKtxVersion = "2.10.0"
val activityComposeVersion = "1.13.0"
val composeBomVersion = "2024.09.00"
val coilVersion = "2.7.0"
val media3Version = "1.4.1"

android {
    namespace = "cn.happyoyster.opensdk.demo"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "cn.happyoyster.opensdk.demo"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // AliVCSDK_ARTC ships native libs for these ABIs only.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation("cn.happyoyster:opensdk:$opensdkVersion")

    // The demo gateway code uses these libraries directly.
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")

    implementation("androidx.core:core-ktx:$coreKtxVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleRuntimeKtxVersion")
    implementation("androidx.activity:activity-compose:$activityComposeVersion")
    implementation(platform("androidx.compose:compose-bom:$composeBomVersion"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("io.coil-kt:coil-compose:$coilVersion")
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
