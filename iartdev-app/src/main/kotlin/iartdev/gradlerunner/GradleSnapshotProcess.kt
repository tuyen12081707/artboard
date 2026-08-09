package iartdev.gradlerunner

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

sealed interface RunEvent {
    data class Line(val text: String) : RunEvent
    data class Finished(val exitCode: Int) : RunEvent
    data class Failed(val error: Throwable) : RunEvent
}

/**
 * Runs `<gradlePath>:artboardSnapshot` in [projectRoot] via the project's own Gradle
 * wrapper, streaming output line-by-line so iArtDev can show it without the user
 * ever opening a terminal.
 *
 * One instance is good for one run; call [cancel] to kill it mid-flight.
 */
class GradleSnapshotProcess {
    @Volatile private var process: Process? = null

    fun run(projectRoot: File, wrapper: File, gradlePath: String): Flow<RunEvent> = flow {
        try {
            if (!isWindows()) wrapper.setExecutable(true)
            val proc = ProcessBuilder(buildCommand(wrapper, gradlePath))
                .directory(projectRoot)
                .redirectErrorStream(true)
                .start()
            process = proc

            val reader = proc.inputStream.bufferedReader()
            while (true) {
                val line = reader.readLine() ?: break
                emit(RunEvent.Line(line))
            }
            emit(RunEvent.Finished(proc.waitFor()))
        } catch (t: Throwable) {
            emit(RunEvent.Failed(t))
        } finally {
            process = null
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Kills the whole process tree, not just the direct child — on Windows the tree is
     * `cmd.exe → gradlew.bat → java.exe`, and destroying only the direct child would
     * strand the actual build.
     */
    fun cancel() {
        val proc = process ?: return
        proc.toHandle().descendants().forEach { it.destroy() }
        proc.destroy()
    }

    companion object {
        private fun isWindows() = System.getProperty("os.name").orEmpty().startsWith("Windows")

        private fun buildCommand(wrapper: File, gradlePath: String): List<String> {
            val task = if (gradlePath.isEmpty()) "artboardSnapshot" else "$gradlePath:artboardSnapshot"
            val invocation = listOf(wrapper.absolutePath, task, "--console=plain")
            // Direct ProcessBuilder invocation of a .bat is unreliable across JDK/Windows
            // combinations; cmd.exe /c is the standard safe wrapping.
            return if (isWindows()) listOf("cmd.exe", "/c") + invocation else invocation
        }

        /**
         * Where `artboardSnapshot` always writes its manifest for a module at [gradlePath]
         * under [projectRoot] — fixed by the plugin (`layout.buildDirectory.dir("artboard/snapshots")`),
         * not discovered heuristically.
         */
        fun manifestFile(projectRoot: File, gradlePath: String): File =
            File(GradleProjectScanner.moduleDir(projectRoot, gradlePath), "build/artboard/snapshots/manifest.json")
    }
}
