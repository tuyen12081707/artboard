package iartdev.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import artboard.host.Studio
import iartdev.theme.IArtDev
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

/** A CLI tool iArtDev knows a conventional config-directory location for. */
private data class KnownTool(val name: String, val defaultPath: String, val note: String)

private val KNOWN_TOOLS = listOf(
    KnownTool("Claude Code", "${System.getProperty("user.home")}/.claude", "Config, memory, and settings live here."),
    KnownTool("Codex CLI", "${System.getProperty("user.home")}/.codex", "OpenAI Codex CLI's config directory."),
)

/**
 * Locates local config directories for AI CLI tools — path discovery only, never reads
 * or stores credentials. Reuses the same "detect a likely default, let the user confirm
 * or override" shape as [iartdev.gradlerunner.GradleProjectScanner]. See
 * docs/REDESIGN_PLAN.md §5 and Decision D4 for why this is scoped to paths, not
 * "injecting accounts."
 */
@Composable
fun ToolPathsScreen(modifier: Modifier = Modifier) {
    val colors = IArtDev.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(28.dp),
    ) {
        BasicText(text = "Tool Paths", style = Studio.type.zoneHeader.copy(color = colors.ink))
        Spacer(Modifier.height(6.dp))
        BasicText(
            text = "Find the local config directory for an AI CLI tool. Read-only path lookup — " +
                "iArtDev never reads or stores credentials from these folders.",
            style = Studio.type.body.copy(color = colors.inkSoft),
        )
        Spacer(Modifier.height(20.dp))

        KNOWN_TOOLS.forEach { tool ->
            ToolRow(name = tool.name, path = tool.defaultPath, note = tool.note)
            Spacer(Modifier.height(10.dp))
        }
        CustomToolRow()
    }
}

@Composable
private fun ToolRow(name: String, path: String, note: String) {
    val colors = IArtDev.colors
    val exists = remember(path) { File(path).isDirectory }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, colors.line, RoundedCornerShape(10.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(text = name, style = Studio.type.label.copy(color = colors.ink))
            Spacer(Modifier.width(8.dp))
            StatusBadge(exists = exists)
        }
        Spacer(Modifier.height(4.dp))
        BasicText(text = path, style = Studio.type.mono.copy(color = colors.inkSoft))
        Spacer(Modifier.height(2.dp))
        BasicText(text = note, style = Studio.type.label.copy(color = colors.inkFaint))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TextAction(label = "Copy Path") {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(path), null)
            }
            if (exists) {
                TextAction(label = "Reveal") {
                    runCatching { Desktop.getDesktop().open(File(path)) }
                }
            }
        }
    }
}

@Composable
private fun CustomToolRow() {
    val colors = IArtDev.colors
    var path by remember { mutableStateOf("") }
    val exists = remember(path) { path.isNotBlank() && File(path).isDirectory }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, colors.line, RoundedCornerShape(10.dp))
            .padding(14.dp),
    ) {
        BasicText(text = "Other tool (e.g. Gravity, or any CLI)", style = Studio.type.label.copy(color = colors.ink))
        Spacer(Modifier.height(2.dp))
        BasicText(
            text = "No known default for this one — type or paste its config path below.",
            style = Studio.type.label.copy(color = colors.inkFaint),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = path,
                onValueChange = { path = it },
                singleLine = true,
                textStyle = Studio.type.mono.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .widthIn(min = 260.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, colors.line, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            Spacer(Modifier.width(10.dp))
            if (path.isNotBlank()) StatusBadge(exists = exists)
        }
    }
}

@Composable
private fun StatusBadge(exists: Boolean) {
    val colors = IArtDev.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (exists) colors.accentWash else colors.surfaceRaised)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        BasicText(
            text = if (exists) "found" else "not found",
            style = Studio.type.badge.copy(color = if (exists) colors.accentInk else colors.inkFaint),
        )
    }
}
