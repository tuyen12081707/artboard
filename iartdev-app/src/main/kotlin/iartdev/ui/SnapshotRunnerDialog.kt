package iartdev.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import artboard.host.Studio
import iartdev.gradlerunner.ArtboardModuleCandidate
import iartdev.gradlerunner.GradleProjectScanner
import iartdev.gradlerunner.GradleSnapshotProcess
import iartdev.gradlerunner.RunEvent
import iartdev.theme.IArtDev
import iartdev.theme.IArtDevColors
import iartdev.theme.IArtDevTheme
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.JFileChooser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ERROR_COLOR = Color(0xFFB3261E)
private const val MAX_LOG_LINES = 4000

private sealed interface RunnerPhase {
    data object PickProject : RunnerPhase
    data class Ready(
        val projectRoot: File,
        val wrapper: File,
        val candidates: List<ArtboardModuleCandidate>,
    ) : RunnerPhase

    data class Running(val startedAtMillis: Long) : RunnerPhase
    data object Succeeded : RunnerPhase
    data class Failed(val reason: String) : RunnerPhase
}

/**
 * Modal "Run Snapshot from Project…" flow: pick a Gradle project, confirm (or
 * override) which module runs `artboardSnapshot`, watch it build, and open the
 * result automatically on success — so the user never has to open a terminal.
 */
@Composable
fun SnapshotRunnerDialog(
    onDismiss: () -> Unit,
    onSnapshotReady: (File) -> Unit,
) {
    IArtDevTheme(darkTheme = isSystemInDarkTheme()) {
        val colors = IArtDev.colors
        val scope = rememberCoroutineScope()
        var phase by remember { mutableStateOf<RunnerPhase>(RunnerPhase.PickProject) }
        var gradlePath by remember { mutableStateOf("") }
        val logLines = remember { mutableStateListOf<String>() }
        var elapsedSeconds by remember { mutableIntStateOf(0) }
        val process = remember { GradleSnapshotProcess() }

        fun appendLog(line: String) {
            logLines.add(line)
            if (logLines.size > MAX_LOG_LINES) logLines.removeAt(0)
        }

        fun pickProject() {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = "Choose your Gradle project root"
            }
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return
            val root = chooser.selectedFile
            val wrapper = GradleProjectScanner.findWrapper(root)
            if (wrapper == null) {
                phase = RunnerPhase.Failed("No gradlew found in ${root.path} — pick the folder that contains it.")
                return
            }
            val candidates = GradleProjectScanner.scanForArtboardModules(root)
            gradlePath = candidates.firstOrNull()?.gradlePath ?: ""
            phase = RunnerPhase.Ready(root, wrapper, candidates)
        }

        fun runSnapshot(root: File, wrapper: File) {
            logLines.clear()
            val startedAt = System.currentTimeMillis()
            phase = RunnerPhase.Running(startedAt)
            scope.launch {
                process.run(root, wrapper, gradlePath).collect { event ->
                    when (event) {
                        is RunEvent.Line -> appendLog(event.text)
                        is RunEvent.Finished -> {
                            if (event.exitCode == 0) {
                                val manifest = GradleSnapshotProcess.manifestFile(root, gradlePath)
                                if (manifest.isFile) {
                                    phase = RunnerPhase.Succeeded
                                    onSnapshotReady(manifest.parentFile)
                                } else {
                                    phase = RunnerPhase.Failed(
                                        "Snapshot finished but no manifest.json at ${manifest.path}. " +
                                            "Double check the module path above.",
                                    )
                                }
                            } else {
                                phase = RunnerPhase.Failed("Gradle exited with code ${event.exitCode} — see log below.")
                            }
                        }
                        is RunEvent.Failed -> phase = RunnerPhase.Failed(event.error.message ?: "Could not start Gradle.")
                    }
                }
            }
        }

        // Elapsed-time readout while running.
        val running = phase as? RunnerPhase.Running
        if (running != null) {
            DisposableEffect(running.startedAtMillis) {
                val job = scope.launch {
                    while (true) {
                        elapsedSeconds = ((System.currentTimeMillis() - running.startedAtMillis) / 1000).toInt()
                        delay(1000)
                    }
                }
                onDispose { job.cancel() }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 480.dp, max = 640.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceRaised)
                    .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    BasicText(text = "Run Snapshot from Project", style = Studio.type.zoneHeader.copy(color = colors.ink))
                    Spacer(Modifier.weight(1f))
                    DialogTextAction(label = "Close") {
                        process.cancel()
                        onDismiss()
                    }
                }
                Spacer(Modifier.height(12.dp))

                when (val current = phase) {
                    RunnerPhase.PickProject -> {
                        BasicText(
                            text = "Point iArtDev at the root of a Gradle project that applies the Artboard plugin.",
                            style = Studio.type.body.copy(color = colors.inkSoft),
                        )
                        Spacer(Modifier.height(14.dp))
                        DialogButton(label = "Choose Project Folder…", onClick = ::pickProject)
                    }

                    is RunnerPhase.Ready -> {
                        if (current.candidates.size > 1) {
                            BasicText(text = "MODULE", style = Studio.type.badge.copy(color = colors.inkFaint))
                            Spacer(Modifier.height(6.dp))
                            current.candidates.forEach { candidate ->
                                ModuleRow(
                                    label = candidate.label,
                                    selected = candidate.gradlePath == gradlePath,
                                    onClick = { gradlePath = candidate.gradlePath },
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                        } else if (current.candidates.isEmpty()) {
                            BasicText(
                                text = "No module here applies the Artboard plugin automatically — " +
                                    "enter its Gradle path below.",
                                style = Studio.type.body.copy(color = colors.inkSoft),
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        BasicText(text = "GRADLE PATH", style = Studio.type.badge.copy(color = colors.inkFaint))
                        Spacer(Modifier.height(4.dp))
                        GradlePathField(value = gradlePath, onValueChange = { gradlePath = it }, colors = colors)
                        Spacer(Modifier.height(14.dp))
                        DialogButton(label = "Run artboardSnapshot") { runSnapshot(current.projectRoot, current.wrapper) }
                    }

                    is RunnerPhase.Running -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicText(text = "Running… ${elapsedSeconds}s", style = Studio.type.mono.copy(color = colors.accentInk))
                            Spacer(Modifier.weight(1f))
                            DialogTextAction(label = "Cancel", onClick = process::cancel)
                        }
                        Spacer(Modifier.height(10.dp))
                        LogPanel(lines = logLines, colors = colors)
                    }

                    RunnerPhase.Succeeded -> {
                        BasicText(text = "Snapshot ready — opening it now.", style = Studio.type.body.copy(color = colors.ink))
                    }

                    is RunnerPhase.Failed -> {
                        BasicText(text = current.reason, style = Studio.type.body.copy(color = ERROR_COLOR))
                        Spacer(Modifier.height(10.dp))
                        LogPanel(lines = logLines, colors = colors)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DialogButton(label = "Try Again") { phase = RunnerPhase.PickProject }
                            if (logLines.isNotEmpty()) {
                                DialogTextAction(label = "Copy Log") {
                                    Toolkit.getDefaultToolkit().systemClipboard
                                        .setContents(StringSelection(logLines.joinToString("\n")), null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogPanel(lines: List<String>, colors: IArtDevColors) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 260.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.canvas)
            .border(1.dp, colors.line, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        items(lines) { line ->
            BasicText(text = line, style = Studio.type.mono.copy(color = colors.inkSoft))
        }
    }
}

@Composable
private fun ModuleRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = IArtDev.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) colors.accentWash else Color.Transparent)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        BasicText(text = label, style = Studio.type.mono.copy(color = if (selected) colors.accentInk else colors.ink))
    }
}

@Composable
private fun GradlePathField(value: String, onValueChange: (String) -> Unit, colors: IArtDevColors) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = Studio.type.mono.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.accent),
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, colors.line, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun DialogButton(label: String, onClick: () -> Unit) {
    val colors = IArtDev.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (hovered) colors.accentInk else colors.accent)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        BasicText(text = label, style = Studio.type.label.copy(color = colors.onAccent))
    }
}

@Composable
private fun DialogTextAction(label: String, onClick: () -> Unit) {
    val colors = IArtDev.colors
    Box(
        modifier = Modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(6.dp),
    ) {
        BasicText(text = label, style = Studio.type.label.copy(color = colors.inkSoft))
    }
}
