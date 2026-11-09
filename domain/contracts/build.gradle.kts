// :domain:contracts - the ports the layers above are allowed to depend on.
//
// Interfaces and value types only: no DataStore, no Android, no implementation. That is what makes
// a use case testable without a device, and what lets the storage engine change without the domain
// noticing. `:data` implements these; `:domain:usecases` consumes them.
plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    // `api`: a repository signature mentions a model type, so consumers need the model too.
    api(project(":domain:model"))
    // Flow is part of the contract (observe returns a Flow), not an implementation detail.
    api(libs.kotlinx.coroutines.core)
}
