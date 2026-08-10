import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

group = "dev.iartdev"
// jpackage requires a leading version component >= 1 for macOS/Windows installers.
version = "1.2.0"

/**
 * iArtDev — standalone desktop viewer for Artboard-exported galleries
 * (`artboardExport` / `artboardSnapshot` output: manifest.json + images/).
 * Not published; packaged as native installers via `compose.desktop.application`.
 */
dependencies {
    implementation(project(":artboard-runtime"))
    implementation(project(":iartdev-extension-api"))
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "iartdev.MainKt"

        nativeDistributions {
            packageName = "iArtDev"
            packageVersion = version.toString()
            description = "Standalone desktop viewer for Artboard preview galleries."
            vendor = "iArtDev"
            copyright = "© ${'$'}{java.time.Year.now()} iArtDev contributors. Apache-2.0."

            // Built per-host by jpackage: macOS CI produces Dmg, Windows CI produces Msi.
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe)

            macOS {
                bundleID = "dev.iartdev.app"
            }

            windows {
                menuGroup = "iArtDev"
                perUserInstall = true
                dirChooser = true
                shortcut = true
            }
        }
    }
}
