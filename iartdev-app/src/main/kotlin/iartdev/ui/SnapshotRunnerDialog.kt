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
import iartdev.gradlerunner.AiFixHelper
import iartdev.gradlerunner.ArtboardModuleCandidate
import iartdev.gradlerunner.GradleProjectScanner
import iartdev.gradlerunner.GradleSnapshotProcess
import iartdev.gradlerunner.KeepAwake
import iartdev.gradlerunner.PluginInstallResult
import iartdev.gradlerunner.RunEvent
import iartdev.gradlerunner.appliesArtboardPlugin
import iartdev.gradlerunner.artboardPluginLine
import iartdev.gradlerunner.installArtboardPlugin
import iartdev.gradlerunner.settingsLooksPluginReady
import iartdev.prefs.RunConfig
import iartdev.prefs.RunConfigStore
import iartdev.prefs.iartdevPrefs
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

    /** Artboard isn't applied at the chosen Gradle path yet — confirm before writing anything. */
    data class ConfirmInstall(
        val previous: Ready,
        val buildFile: File,
        val previewLine: String,
        val settingsWarning: String?,
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
        val keepAwake = remember { KeepAwake() }
        DisposableEffect(Unit) { onDispose { keepAwake.stop() } }
        var lastAttemptedRoot by remember { mutableStateOf<File?>(null) }
        val installedAiClis = remember { AiFixHelper.AUTOMATABLE_CLIS.filter(AiFixHelper::isInstalled) }
        val aiOutput = remember { mutableStateListOf<String>() }
        var aiRunning by remember { mutableStateOf(false) }

        fun runAiDiagnosis(binary: String) {
            val root = lastAttemptedRoot ?: return
            aiOutput.clear()
            aiRunning = true
            val prompt = AiFixHelper.diagnosticPrompt(root, gradlePath, logLines.joinToString("\n"))
            scope.launch {
                AiFixHelper.runDiagnosis(binary, root, prompt).collect { event ->
                    when (event) {
                        is RunEvent.Line -> aiOutput.add(event.text)
                        is RunEvent.Finished -> aiRunning = false
                        is RunEvent.Failed -> {
                            aiRunning = false
                            aiOutput.add("Could not run $binary: ${event.error.message}")
                        }
                    }
                }
            }
        }

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
            lastAttemptedRoot = root
            val startedAt = System.currentTimeMillis()
            phase = RunnerPhase.Running(startedAt)
            // Android snapshot mode's Robolectric render can run for minutes — keep the
            // machine awake for the duration so a closed lid / screen timeout doesn't
            // stall it. Stopped in every terminal branch below.
            keepAwake.start()
            scope.launch {
                process.run(root, wrapper, gradlePath).collect { event ->
                    when (event) {
                        is RunEvent.Line -> appendLog(event.text)
                        is RunEvent.Finished -> {
                            keepAwake.stop()
                            if (event.exitCode == 0) {
                                val manifest = GradleSnapshotProcess.manifestFile(root, gradlePath)
                                if (manifest.isFile) {
                                    phase = RunnerPhase.Succeeded
                                    RunConfigStore.save(iartdevPrefs(), RunConfig(root.absolutePath, gradlePath))
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
                        is RunEvent.Failed -> {
                            keepAwake.stop()
                            phase = RunnerPhase.Failed(event.error.message ?: "Could not start Gradle.")
                        }
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
                            text = "Choose the project's ROOT folder — the one that directly contains gradlew " +
                                "(and settings.gradle.kts). Not a module subfolder like app/ or feature/ui/ — " +
                                "picking one of those fails with \"No gradlew found\".",
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
                                text = "No module here already has Artboard applied — type the Gradle path of " +
                                    "your Compose UI module below and iArtDev will offer to set it up.",
                                style = Studio.type.body.copy(color = colors.inkSoft),
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        BasicText(text = "GRADLE PATH", style = Studio.type.badge.copy(color = colors.inkFaint))
                        Spacer(Modifier.height(4.dp))
                        BasicText(
                            text = "Type the module's Gradle path (not a folder to browse to) — same as you'd " +
                                "pass to ./gradlew, e.g. :feature:my-module or :app. Leave empty for the root project.",
                            style = Studio.type.label.copy(color = colors.inkFaint),
                        )
                        Spacer(Modifier.height(6.dp))
                        GradlePathField(
                            value = gradlePath,
                            onValueChange = { gradlePath = it },
                            placeholder = ":feature:my-module",
                            colors = colors,
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            DialogButton(label = "Run artboardSnapshot") {
                                val buildFile = GradleProjectScanner.moduleBuildFile(current.projectRoot, gradlePath)
                                if (buildFile.isFile && buildFile.appliesArtboardPlugin()) {
                                    runSnapshot(current.projectRoot, current.wrapper)
                                } else {
                                    val settingsFile = GradleProjectScanner.findSettingsFile(current.projectRoot)
                                    val settingsWarning = if (settingsFile == null || !settingsLooksPluginReady(settingsFile)) {
                                        "Heads up: ${settingsFile?.name ?: "settings.gradle.kts"} doesn't obviously list " +
                                            "mavenCentral() for plugin resolution — if the run below fails to resolve " +
                                            "the plugin, add it there."
                                    } else {
                                        null
                                    }
                                    phase = RunnerPhase.ConfirmInstall(
                                        previous = current,
                                        buildFile = buildFile,
                                        previewLine = artboardPluginLine(),
                                        settingsWarning = settingsWarning,
                                    )
                                }
                            }
                            DialogTextAction(label = "Copy Command") {
                                val task = if (gradlePath.isEmpty()) "artboardSnapshot" else "$gradlePath:artboardSnapshot"
                                Toolkit.getDefaultToolkit().systemClipboard
                                    .setContents(StringSelection("./gradlew $task"), null)
                            }
                        }
                    }

                    is RunnerPhase.ConfirmInstall -> {
                        BasicText(
                            text = "Artboard isn't applied at ${gradlePath.ifEmpty { "(root project)" }} yet. " +
                                "iArtDev will add this line to ${current.buildFile.name}'s plugins { } block:",
                            style = Studio.type.body.copy(color = colors.inkSoft),
                        )
                        Spacer(Modifier.height(8.dp))
                        BasicText(text = current.previewLine, style = Studio.type.mono.copy(color = colors.ink))
                        if (current.settingsWarning != null) {
                            Spacer(Modifier.height(8.dp))
                            BasicText(text = current.settingsWarning, style = Studio.type.body.copy(color = ERROR_COLOR))
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DialogButton(label = "Apply & Run") {
                                when (val result = installArtboardPlugin(current.buildFile)) {
                                    is PluginInstallResult.Failed ->
                                        phase = RunnerPhase.Failed(result.reason)
                                    PluginInstallResult.AlreadyApplied, PluginInstallResult.Installed ->
                                        runSnapshot(current.previous.projectRoot, current.previous.wrapper)
                                }
                            }
                            DialogTextAction(label = "Cancel") { phase = current.previous }
                        }
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
                                DialogTextAction(label = "Copy Diagnostic Prompt") {
                                    val root = lastAttemptedRoot
                                    if (root != null) {
                                        val prompt = AiFixHelper.diagnosticPrompt(root, gradlePath, logLines.joinToString("\n"))
                                        Toolkit.getDefaultToolkit().systemClipboard
                                            .setContents(StringSelection(prompt), null)
                                    }
                                }
                            }
                        }
                        if (installedAiClis.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            BasicText(text = "STUCK? ASK AN AI CLI", style = Studio.type.badge.copy(color = colors.inkFaint))
                            Spacer(Modifier.height(6.dp))
                            BasicText(
                                text = "Runs the already-authenticated CLI you have installed, read-only — it " +
                                    "explains a fix, it doesn't edit anything.",
                                style = Studio.type.label.copy(color = colors.inkFaint),
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                installedAiClis.forEach { binary ->
                                    DialogButton(label = if (aiRunning) "Asking $binary…" else "Diagnose with $binary") {
                                        if (!aiRunning) runAiDiagnosis(binary)
                                    }
                                }
                            }
                            if (aiOutput.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                LogPanel(lines = aiOutput, colors = colors)
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
private fun GradlePathField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    colors: IArtDevColors,
) {
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
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    BasicText(text = placeholder, style = Studio.type.mono.copy(color = colors.inkFaint))
                }
                innerTextField()
            }
        },
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
