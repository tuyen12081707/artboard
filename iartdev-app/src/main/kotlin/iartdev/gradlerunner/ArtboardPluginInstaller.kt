package iartdev.gradlerunner

import java.io.File

private const val ARTBOARD_PLUGIN_ID = "io.github.tuyen12081707.artboard"

/** Version applied by [installArtboardPlugin] when the caller doesn't pin one. */
const val DEFAULT_ARTBOARD_VERSION: String = "0.2.2"

/** Whether [this] build file already applies the Artboard plugin, by any means. */
fun File.appliesArtboardPlugin(): Boolean =
    runCatching { readText() }.getOrDefault("").contains(ARTBOARD_PLUGIN_ID)

/** The exact line [installArtboardPlugin] would insert — shown to the user before writing anything. */
fun artboardPluginLine(version: String = DEFAULT_ARTBOARD_VERSION): String =
    "    id(\"$ARTBOARD_PLUGIN_ID\") version \"$version\""

/** Whether [settingsFile] looks able to resolve the plugin (Maven Central listed for plugin resolution). */
fun settingsLooksPluginReady(settingsFile: File): Boolean =
    runCatching { settingsFile.readText() }.getOrDefault("").contains("mavenCentral")

sealed interface PluginInstallResult {
    data object AlreadyApplied : PluginInstallResult
    data object Installed : PluginInstallResult
    data class Failed(val reason: String) : PluginInstallResult
}

/**
 * Inserts [artboardPluginLine] into [buildFile]'s `plugins { }` block, right before the
 * closing brace — i.e. applied *last*, after every other plugin already listed.
 *
 * Order matters here, not just style: Kotlin/Android/KSP-based plugins configure
 * themselves against whatever targets and source sets already exist when they're
 * applied. Inserting Artboard first (before `kotlinMultiplatform`/the Android library
 * plugin/etc. get a chance to set up their targets) can fail with errors like
 * `Configuration with name 'kspAndroid' not found` — reproduced against a real KMP
 * module during development. Applying last avoids that class of ordering bug.
 *
 * Deliberately simple text surgery, not a Gradle KTS parser — it assumes the
 * `plugins { }` block itself isn't nested inside other braces (true for every real
 * build file this was tested against; `plugins {}` blocks don't nest in practice).
 * Callers must show [artboardPluginLine] to the user and get explicit confirmation
 * before calling this: it mutates a real file in a project iArtDev doesn't own.
 */
fun installArtboardPlugin(buildFile: File, version: String = DEFAULT_ARTBOARD_VERSION): PluginInstallResult {
    val text = runCatching { buildFile.readText() }
        .getOrElse { return PluginInstallResult.Failed("Could not read ${buildFile.path}: ${it.message}") }
    if (text.contains(ARTBOARD_PLUGIN_ID)) return PluginInstallResult.AlreadyApplied

    val pluginsBlockOpen = Regex("""plugins\s*\{""").find(text)
        ?: return PluginInstallResult.Failed(
            "No plugins { } block found in ${buildFile.name}. Add this line inside it yourself:\n" +
                artboardPluginLine(version),
        )
    val blockClose = text.indexOf('}', pluginsBlockOpen.range.last + 1)
    if (blockClose < 0) {
        return PluginInstallResult.Failed(
            "Could not find the end of the plugins { } block in ${buildFile.name}. " +
                "Add this line inside it yourself:\n" + artboardPluginLine(version),
        )
    }
    val updated = text.substring(0, blockClose) + artboardPluginLine(version) + "\n" + text.substring(blockClose)
    return runCatching {
        buildFile.writeText(updated)
        PluginInstallResult.Installed
    }.getOrElse { PluginInstallResult.Failed("Could not write ${buildFile.path}: ${it.message}") }
}
