// :core:model — the innermost layer. Pure Kotlin (JVM), NO Android dependencies.
// Keeping it Android-free is what lets the layers above be unit-tested with zero emulator.
plugins {
    `java-library`          // gives us the `api`/`implementation` split
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization) // value types here are @Serializable
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // `api` = consumers of :core:model also get serialization (their types embed ours).
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
