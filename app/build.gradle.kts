plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

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

    /*
     * The signing key is committed, and that is deliberate.
     *
     * Android identifies an app by (packageName, signing certificate). Every APK this family
     * has ever shipped is a public GitHub release, so the certificate is already public and
     * permanently so — a rotation was attempted once and reverted, because changing the cert
     * breaks in-place updates with an opaque "Failure: Invalid" and the only cure is an
     * uninstall, which wipes the user's data.
     *
     * So there is no secret to protect here, and pretending otherwise costs something real: a
     * repository without a KEYSTORE_B64 secret builds an APK signed with the throwaway debug
     * key, which will not update over a release and cannot be fixed later without an
     * uninstall. Committing the key means the very first release is already the permanent
     * identity of the app.
     *
     * The fallback below must be the debug config and never `null`. `signingConfig = null`
     * produces an *unsigned* release, which Android will not install at all, and AGP names it
     * `app-release-unsigned.apk` — so every path written for `app-release.apk` quietly refers
     * to a file that is not there.
     */
    val keystoreFile = rootProject.file("keystore/brightrolodex.jks")
    val keystorePassword: String = System.getenv("KEYSTORE_PASSWORD")
        ?.takeUnless(String::isBlank)
        ?: rootProject.file("keystore/password.txt")
            .takeIf { it.exists() }
            ?.readText()
            ?.trim()
            .orEmpty()
    val canSignRelease = keystoreFile.exists() && keystorePassword.isNotEmpty()

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = "brightrolodex"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (canSignRelease) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
