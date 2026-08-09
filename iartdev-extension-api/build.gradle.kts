plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

group = "dev.iartdev"

/**
 * Contract module for iArtDev plugins. Kept dependency-light (just the pieces of
 * Artboard/Compose an extension needs to describe itself) so third-party extension
 * jars don't have to pull in the whole app.
 */
dependencies {
    implementation(project(":artboard-runtime"))
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
}
