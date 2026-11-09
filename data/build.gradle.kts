// :data — persistence (Jetpack DataStore). An Android library because DataStore is.
// Holds widget configs, instance bindings and the profile; exposes repositories only.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.mementomori.lifeclock.core.data"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":domain:model"))
    // The repository interfaces this module implements. `api` so a consumer that only depends on
    // :data can still name the contract it is handed.
    api(project(":domain:contracts"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    // Android unit tests run on the JVM via Robolectric (no device needed).
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
