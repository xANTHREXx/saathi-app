import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "app.saathi"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.saathi"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        // The backend function: https://<ref>.supabase.co/functions/v1/api, or the local Deno server from the emulator.
        // Updates are only accepted from here (forks set UPDATE_URL_PREFIX in local.properties).
        buildConfigField("String", "UPDATE_URL_PREFIX", "\"${localProps.getProperty("UPDATE_URL_PREFIX", "https://github.com/xANTHREXx/saathi-app/releases/download/")}\"")
        buildConfigField("String", "API_URL","\"${localProps.getProperty("API_URL", "http://10.0.2.2:8000/api").trimEnd('/')}\"")
    }

    // The release key lives outside the repo; local.properties only points at it. Never log these values.
    // Without all four keys the release build stays unsigned, so a wrongly signed APK can't be published by accident.
    val releaseKeys = listOf("RELEASE_STORE_FILE", "RELEASE_STORE_PASSWORD", "RELEASE_KEY_ALIAS", "RELEASE_KEY_PASSWORD")
    val canSignRelease = releaseKeys.all { !localProps.getProperty(it).isNullOrBlank() }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = rootProject.file(localProps.getProperty("RELEASE_STORE_FILE"))
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (canSignRelease) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    // On-phone text recognition for plate photos; the bundled model works offline.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    testImplementation("junit:junit:4.13.2")
}
