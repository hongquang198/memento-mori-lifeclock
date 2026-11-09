// Settings = the project's "table of contents". Gradle reads this first.
// `pluginManagement` / `dependencyResolutionManagement` tell Gradle WHERE to fetch
// build plugins and libraries from (Google's Maven for AndroidX/AGP, Maven Central for
// everything else). This mirrors `gradle/wrapper` configuring WHICH Gradle runs.
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Fail the build if a module declares its own repo — keeps dependency sources in one place.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "memento-mori-lifeclock"

// The layer map. A module's Gradle path names its layer, so the structure answers "which layer is
// this?" without a table to memorise:
//
//   :domain:*  the domain layer - pure JVM, no Android on the classpath. Enforced by
//              ArchitectureGuardTest, not by discipline.
//   :data      the data layer - repositories over DataStore.
//   :ui:*      Android UI surfaces. Compose lives in :app; Glance (app widgets) in :ui:widget.
//   :app       the composition root and the Compose UI. The one conventional exception to the
//              prefix rule: an Android application module is always called :app.
//
// Adding a module? Ask which layer it belongs to, then put it under that prefix. If it fits none,
// the design is wrong, not the naming.
include(":domain:model")
include(":domain:time")
include(":domain:mortality")
include(":domain:scheduler")
include(":data")
include(":ui:widget")
include(":app")
