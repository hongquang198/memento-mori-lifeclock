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

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    implementation(project(":core:model"))
    implementation(project(":core:time"))
    implementation(project(":core:mortality"))
    implementation(project(":core:data"))
    implementation(project(":core:scheduler"))
    // Outer layers
    implementation(project(":widget"))

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
