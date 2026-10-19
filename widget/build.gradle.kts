// :widget — HOW a widget is drawn: Glance UI, the rasterized ring bitmap, Chronometer.
// Glance is "Compose for app widgets", so it needs the Compose compiler plugin.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.mementomori.lifeclock.widget"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:time"))
    implementation(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Glance provides a JVM unit-test harness (`runGlanceAppWidgetUnitTest`).
    testImplementation(libs.junit4)
    testImplementation(libs.androidx.glance.appwidget.testing)
    testImplementation(libs.kotlinx.coroutines.test)
}
