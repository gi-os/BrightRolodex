plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreFile = providers.environmentVariable("KEYSTORE_FILE").orNull
val keystorePassword = providers.environmentVariable("KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("KEY_PASSWORD").orNull

android {
    namespace = "com.gios.brightrolodex"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.gios.brightrolodex"
        // Matches the rest of the family. The LPIII is on Android 14.
        minSdk = 33
        targetSdk = 35
        // CI overwrites both from the workflow run number; see .github/workflows/build.yml.
        //
        // Only the major.minor here is read — the workflow takes them and appends the run
        // number as the patch. A release whose notes announce a new minor has to have this
        // bumped first, or the tag says 1.0.7 while RELEASE_NOTES.md says v1.1.
        versionCode = 1
        versionName = "1.0.0"

        // The LPIII is arm64 only. Nothing here ships a native library, but keeping the
        // filter means a stray transitive .so can never quadruple the APK unnoticed.
        ndk { abiFilters += "arm64-v8a" }
    }

    val releaseSigning = if (!keystoreFile.isNullOrBlank() && !keystorePassword.isNullOrBlank()
        && !releaseKeyAlias.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()
    ) {
        signingConfigs.create("release") {
            storeFile = rootProject.file(keystoreFile)
            storePassword = keystorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    } else {
        null
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = releaseSigning
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    testOptions {
        unitTests {
            // android.jar on the unit-test classpath is a stub whose every method throws
            // "not mocked". The store is JSON, so its tests would die on the first org.json
            // call without this and the real implementation in testImplementation below.
            isReturnDefaultValues = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // The face model, the path spec, the store and the search are deliberately free of
    // Android imports, so they can be tested on the JVM rather than on a phone.
    testImplementation("junit:junit:4.13.2")
    // Shadows android.jar's stubbed org.json so the store round-trip is actually exercised.
    testImplementation("org.json:json:20240303")
}
