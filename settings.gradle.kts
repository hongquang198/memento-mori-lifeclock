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

// The module map from docs/design.md §1. Each `include` maps a folder to a Gradle module.
// `:core:*` are the pure/inner layers; `:widget`/`:feature:*` are Android UI; `:app` wires it up.
include(":app")
include(":core:model")
include(":core:time")
include(":core:mortality")
include(":core:data")
include(":core:scheduler")
include(":widget")
