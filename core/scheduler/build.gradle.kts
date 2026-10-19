// :core:scheduler — WHEN/HOW widgets refresh (WorkManager + boundary alarms + receivers).
// Depends on :core:data so it can read which widgets exist; knows nothing about drawing.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.mementomori.lifeclock.core.scheduler"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":core:data"))
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
