// :domain:scheduler — WHEN a widget needs redrawing: refresh granularity and the next boundary
// instant (midnight, hour, ...). Pure Kotlin: no Android, no WorkManager, no clocks.
// The Android side (WorkManager, AlarmManager, broadcast receivers) lives in :ui:widget, because it
// has to call Glance to redraw - and :domain must never depend on :ui:widget.
plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":domain:model"))

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
