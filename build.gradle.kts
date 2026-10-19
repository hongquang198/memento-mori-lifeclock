// Root build file. It applies NOTHING to the project itself — it only *declares* the
// plugins each module may use, with `apply false`. This is the modern Gradle pattern:
// versions are resolved once here; each module opts in via its own `plugins { }` block.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// Convenience: `./gradlew clean` across all modules.
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
