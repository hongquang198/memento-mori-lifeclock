// :domain:usecases - the operations the app can perform, expressed over ports.
//
// A use case exists when one user action needs more than one thing to happen in order: save a
// config, then redraw the widgets. The ViewModel calls it and shows the result; it does not know
// that a save is followed by a redraw, and it never touches WorkManager.
//
// Pure JVM on purpose: these are the rules of the app, and they should be testable in milliseconds
// with fake repositories, not on an emulator.
plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":domain:contracts"))
    implementation(project(":domain:model"))

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
