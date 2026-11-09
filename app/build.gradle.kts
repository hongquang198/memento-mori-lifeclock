import java.util.Properties

// :app — the composition root: wires all modules together, hosts the Hilt graph,
// declares the APK entry point (Application + Activity) and the app's manifest.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)   // annotation processing entry point (for Hilt)
    alias(libs.plugins.hilt)  // dependency injection
}

android {
    namespace = "dev.mementomori.lifeclock"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.mementomori.lifeclock"
        minSdk = 26          // Android 8.0: native java.time, no desugaring needed
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // Release signing lives outside the repo: keystore.properties points at a .jks that must never
    // be committed. Without the file the build still succeeds, but the APK is unsigned and Android
    // refuses to install it - which is exactly what assembleRelease produced before this block.
    // (Flutter hides this: its template signs release with the debug key, so `flutter build apk`
    // installs anywhere. AGP has no such default.)
    val signingProperties = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }
    val hasReleaseKey = signingProperties.getProperty("storeFile") != null

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Signed release output: app-release.apk instead of app-release-unsigned.apk.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    // Inner layers
    implementation(project(":domain:model"))
    implementation(project(":domain:time"))
    implementation(project(":domain:mortality"))
    implementation(project(":data"))
    implementation(project(":domain:scheduler"))
    implementation(project(":domain:contracts"))
    implementation(project(":domain:usecases"))
    // Outer layers
    implementation(project(":ui:widget"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit4)
}
