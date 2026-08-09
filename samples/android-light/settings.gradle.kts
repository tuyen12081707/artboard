pluginManagement {
    includeBuild("../..")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "artboard-android-light"

includeBuild("../..") {
    dependencySubstitution {
        substitute(module("io.github.tuyen12081707.artboard:artboard-codegen"))
            .using(project(":artboard-codegen"))
        substitute(module("io.github.tuyen12081707.artboard:artboard-runtime"))
            .using(project(":artboard-runtime"))
        substitute(module("io.github.tuyen12081707.artboard:artboard-viewer-dist"))
            .using(project(":artboard-viewer-dist"))
    }
}
