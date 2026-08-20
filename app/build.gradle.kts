import java.util.Properties

plugins {
    // AGP 9 has built-in Kotlin support — no org.jetbrains.kotlin.android here.
    // The Compose compiler plugin is still applied separately, at a version that
    // matches AGP's built-in Kotlin.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// Release signing
//
// Credentials come from the environment (CI secrets) or, for local release
// builds, an untracked keystore.properties in the project root. When neither is
// present the release build is simply left unsigned, so a plain checkout — and a
// pull request from a fork, which cannot see secrets — can still run
// assembleRelease without failing.
// ---------------------------------------------------------------------------
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingSecret(envName: String, propertyName: String): String? =
    (System.getenv(envName) ?: keystoreProperties.getProperty(propertyName))
        ?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingSecret("RELEASE_STORE_FILE", "storeFile")
val releaseStorePassword = signingSecret("RELEASE_STORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingSecret("RELEASE_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingSecret("RELEASE_KEY_PASSWORD", "keyPassword")

val canSignRelease = releaseStoreFile != null &&
    releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null &&
    file(releaseStoreFile).exists()

android {
    namespace = "com.wakeup.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.wakeup.app"
        // 33 (Android 13) keeps the permission handling simple: POST_NOTIFICATIONS,
        // canScheduleExactAlarms(), and getStreamMinVolume() all exist unconditionally.
        minSdk = 33
        targetSdk = 37
        // CI overrides these so a published APK's version matches its release.
        // versionCode must increase for an install to be upgradable.
        versionCode = (findProperty("appVersionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("appVersionName") as String?) ?: "1.0"
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (canSignRelease) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
