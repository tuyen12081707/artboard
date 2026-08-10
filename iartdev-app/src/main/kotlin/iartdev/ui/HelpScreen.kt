package iartdev.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import artboard.host.Studio
import iartdev.theme.IArtDev

private data class HelpEntry(val title: String, val body: String)

private val ENTRIES = listOf(
    HelpEntry(
        "Gallery",
        "Shows whatever snapshot/live gallery is currently open. Empty until you run a " +
            "snapshot or open an existing manifest.json folder.",
    ),
    HelpEntry(
        "Run Snapshot…",
        "Point this at your Gradle project's ROOT folder — the one containing gradlew, " +
            "not a module subfolder — then pick or type the Gradle module path " +
            "(e.g. :feature:my-module). iArtDev applies the Artboard plugin for you if " +
            "it isn't there yet, runs artboardSnapshot, and opens the result automatically.",
    ),
    HelpEntry(
        "Sync (bottom bar)",
        "Re-runs the last project + module you successfully snapshotted, without walking " +
            "back through the picker. Only enabled after at least one successful run.",
    ),
    HelpEntry(
        "Tool Paths",
        "Looks up local config-directory paths for AI CLI tools (Claude Code, Codex CLI, " +
            "or any other tool you name). Read-only path lookup — never reads or stores " +
            "credentials.",
    ),
    HelpEntry(
        "Change Folder…",
        "Opens a different already-rendered snapshot folder directly (one containing a " +
            "manifest.json), skipping Gradle entirely.",
    ),
)

/** Static reference panel — what each screen/action does, plus build info. See docs/REDESIGN_PLAN.md, Decision D6. */
@Composable
fun HelpScreen(modifier: Modifier = Modifier) {
    val colors = IArtDev.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
    ) {
        BasicText(text = "Help", style = Studio.type.zoneHeader.copy(color = colors.ink))
        Spacer(Modifier.height(16.dp))

        ENTRIES.forEach { entry ->
            BasicText(text = entry.title, style = Studio.type.label.copy(color = colors.ink))
            Spacer(Modifier.height(2.dp))
            BasicText(
                text = entry.body,
                style = Studio.type.body.copy(color = colors.inkSoft),
                modifier = Modifier.widthIn(max = 520.dp),
            )
            Spacer(Modifier.height(16.dp))
        }

        BasicText(text = "About", style = Studio.type.label.copy(color = colors.ink))
        Spacer(Modifier.height(4.dp))
        BasicText(text = "iArtDev ${appVersion()}", style = Studio.type.mono.copy(color = colors.inkSoft))
        Spacer(Modifier.height(2.dp))
        BasicText(
            text = "Standalone viewer for Artboard preview galleries. Apache-2.0.",
            style = Studio.type.body.copy(color = colors.inkFaint),
        )
        Spacer(Modifier.height(2.dp))
        BasicText(
            text = "Plan and decisions behind this UI: iartdev-app/docs/REDESIGN_PLAN.md",
            style = Studio.type.mono.copy(color = colors.inkFaint),
        )
    }
}

/** Mirrors artboard.gradle.ArtboardPlugin's own pluginVersion() lookup: JAR manifest, falling back to a dev label. */
private fun appVersion(): String =
    HelpEntry::class.java.`package`?.implementationVersion
        ?.takeIf(String::isNotBlank)
        ?: "dev build"
