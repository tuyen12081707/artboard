package iartdev.gradlerunner

import java.io.File

/** A module found to apply the Artboard Gradle plugin. */
data class ArtboardModuleCandidate(
    /** Gradle project path, e.g. `:ui` or `:app:feature-ui`. Empty means the root project itself. */
    val gradlePath: String,
    val buildFile: File,
) {
    /** Human-readable label for pickers: `:ui` or `(root project)`. */
    val label: String get() = gradlePath.ifEmpty { "(root project)" }
}

/**
 * Locates the Gradle wrapper in, and Artboard-enabled modules under, a user-chosen
 * project root — the input to iArtDev's in-app "Run Snapshot" flow (see
 * `GradleSnapshotProcess`). Pure file I/O, no Gradle/Compose dependency, so it's
 * cheap to unit test.
 */
object GradleProjectScanner {
    private const val ARTBOARD_PLUGIN_ID = "io.github.crowded-libs.artboard"
    private val PRUNED_DIRECTORY_NAMES = setOf(
        ".git", ".gradle", ".idea", ".kotlin", "build", "node_modules", "out", "dist",
    )
    private val BUILD_FILE_NAMES = setOf("build.gradle.kts", "build.gradle")

    /** The wrapper script for [projectRoot]'s platform, or null if this isn't a Gradle root. */
    fun findWrapper(projectRoot: File): File? {
        val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")
        val wrapper = File(projectRoot, if (isWindows) "gradlew.bat" else "gradlew")
        return wrapper.takeIf { it.isFile }
    }

    /**
     * Modules under [projectRoot] whose build file mentions the Artboard plugin id.
     *
     * Known limitation: this text-matches a literal `id("io.github.crowded-libs.artboard")`
     * (how every sample in this project applies it) and will miss version-catalog
     * `alias(...)` application, convention-plugin indirection, a `settings.gradle.kts`
     * that remaps a module's `projectDir`, or a Groovy `build.gradle`. Callers must
     * offer a manual Gradle-path override alongside this list, not rely on it alone.
     */
    fun scanForArtboardModules(projectRoot: File): List<ArtboardModuleCandidate> {
        if (!projectRoot.isDirectory) return emptyList()
        return projectRoot.walkTopDown()
            .onEnter { it == projectRoot || (it.name !in PRUNED_DIRECTORY_NAMES && !it.name.startsWith(".")) }
            .filter { it.isFile && it.name in BUILD_FILE_NAMES }
            .filter { runCatching { it.readText() }.getOrDefault("").contains(ARTBOARD_PLUGIN_ID) }
            .map { buildFile ->
                val relative = buildFile.parentFile.relativeTo(projectRoot).path
                val gradlePath = if (relative.isEmpty()) "" else ":" + relative.replace(File.separatorChar, ':')
                ArtboardModuleCandidate(gradlePath, buildFile)
            }
            .sortedBy { it.gradlePath }
            .toList()
    }
}
