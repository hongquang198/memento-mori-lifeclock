// :core:mortality — pure Kotlin life-statistics engine (SSA/WHO/ATUS math).
// Reference tables live in src/main/resources (JVM resources), NOT Android assets,
// so this module stays Android-free and testable. JVM resources also ship on Android.
plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:model"))
    // :core:time is added with NET_FREE_HOURS, the one stat that reuses its time math.

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
