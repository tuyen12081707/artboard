package iartdev.gradlerunner

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Hands a failed Artboard setup to a locally installed, already-authenticated AI CLI
 * for diagnosis — never stores or handles credentials, never edits files itself.
 *
 * "Log into Claude/Codex/Antigravity" was read literally as an OAuth/credential flow
 * iArtDev would own — that's a security-sensitive shape not worth inventing on an
 * assumption for a desktop tool with no backend. What this does instead: the user
 * already runs `claude login` / `codex login` etc. themselves, in their own terminal,
 * however that CLI normally wants it. iArtDev just shells out to the binary that
 * session already lives in — same trust boundary [GradleSnapshotProcess] already uses
 * for the project's own `./gradlew`. See docs/REDESIGN_PLAN.md, Decision D7.
 *
 * Deliberately read-only: the prompt asks the CLI to diagnose and explain a fix, not
 * to apply one. Autonomously letting a subprocess edit a user's real project files
 * from inside a GUI action, with permission flags this codebase can't verify are safe
 * by default, is a materially different (and much riskier) feature than "explain what's
 * wrong" — out of scope here.
 */
object AiFixHelper {
    /**
     * CLI binaries this can detect and run automatically. "Antigravity" wasn't
     * identifiable as a specific tool/binary from the request alone, so it isn't
     * listed here — [diagnosticPrompt] is still useful pasted into it or any other
     * tool manually via "Copy Diagnostic Prompt".
     */
    val AUTOMATABLE_CLIS = listOf("claude", "codex")

    fun isInstalled(binary: String): Boolean = runCatching {
        val locator = if (isWindows()) "where" else "which"
        ProcessBuilder(locator, binary).redirectErrorStream(true).start().waitFor() == 0
    }.getOrDefault(false)

    fun diagnosticPrompt(projectRoot: File, gradlePath: String, errorLog: String): String = buildString {
        appendLine("The Artboard Gradle plugin (io.github.tuyen12081707.artboard) failed to run in this project.")
        appendLine("Project root: ${projectRoot.absolutePath}")
        appendLine("Gradle module path attempted: ${gradlePath.ifBlank { "(root project)" }}")
        appendLine()
        appendLine("Diagnose why, and suggest the exact fix (which module to target, or what to change).")
        appendLine("Explain the fix — do not make any edits yourself.")
        appendLine()
        appendLine("Gradle output (most recent lines):")
        append(errorLog.takeLast(4000))
    }

    /**
     * Runs `<binary> -p <prompt>` read-only in [projectRoot], streaming output like
     * [GradleSnapshotProcess]. Confirmed non-interactive/print-mode syntax for Claude
     * Code; best-effort for other CLIs on [AUTOMATABLE_CLIS] — if a given tool's flags
     * differ, the process just exits with an error that shows up in the log like any
     * other failed command, rather than silently doing the wrong thing.
     */
    fun runDiagnosis(binary: String, projectRoot: File, prompt: String): Flow<RunEvent> = flow {
        try {
            val proc = ProcessBuilder(binary, "-p", prompt)
                .directory(projectRoot)
                .redirectErrorStream(true)
                .start()
            val reader = proc.inputStream.bufferedReader()
            while (true) {
                val line = reader.readLine() ?: break
                emit(RunEvent.Line(line))
            }
            emit(RunEvent.Finished(proc.waitFor()))
        } catch (t: Throwable) {
            emit(RunEvent.Failed(t))
        }
    }.flowOn(Dispatchers.IO)

    private fun isWindows() = System.getProperty("os.name").orEmpty().startsWith("Windows")
}
