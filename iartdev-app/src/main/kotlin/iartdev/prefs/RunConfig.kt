package iartdev.prefs

import java.util.prefs.Preferences

/** The last (project root, Gradle module path) a snapshot was successfully run for. */
data class RunConfig(val projectRootPath: String, val gradlePath: String)

/**
 * Remembers the single most-recent successful run configuration so the bottom bar's
 * "Sync" action can re-run it without walking back through [iartdev.ui.SnapshotRunnerDialog]'s
 * full picker flow.
 *
 * Deliberately one global entry, not per-folder: iArtDev is used against one project at
 * a time in practice, and a per-folder map adds bookkeeping (eviction, key normalization)
 * for a case that doesn't come up.
 */
object RunConfigStore {
    private const val ROOT_KEY = "lastRun.projectRoot"
    private const val GRADLE_PATH_KEY = "lastRun.gradlePath"

    fun load(prefs: Preferences): RunConfig? {
        val root = prefs.get(ROOT_KEY, null) ?: return null
        val gradlePath = prefs.get(GRADLE_PATH_KEY, "")
        return RunConfig(root, gradlePath)
    }

    fun save(prefs: Preferences, config: RunConfig) {
        prefs.put(ROOT_KEY, config.projectRootPath)
        prefs.put(GRADLE_PATH_KEY, config.gradlePath)
    }
}
