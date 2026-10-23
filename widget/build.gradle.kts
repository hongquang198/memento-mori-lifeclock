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

    // Robolectric renders real pixels and needs the merged resources (colors, layouts) to do it.
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:time"))
    implementation(project(":core:data"))
    implementation(project(":core:scheduler"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    // Reading bindings/configs is suspend + Flow based; do not rely on Glance's transitive copy.
    implementation(libs.kotlinx.coroutines.core)

    // M0 fix verification: the boot refresh must run outside a broadcast budget.
    implementation(libs.androidx.work.runtime.ktx)

    // Glance provides a JVM unit-test harness (`runGlanceAppWidgetUnitTest`).
    testImplementation(libs.junit4)
    testImplementation(libs.androidx.glance.appwidget.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    // Bitmap/Canvas are stubs on the JVM; Robolectric renders real pixels for the renderer tests.
    testImplementation(libs.robolectric)
}
