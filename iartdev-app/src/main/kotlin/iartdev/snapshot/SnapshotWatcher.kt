package iartdev.snapshot

import java.io.File
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Watches [manifestFile]'s parent directory and calls [onChanged] (debounced)
 * whenever the manifest itself is rewritten — e.g. by re-running `artboardSnapshot`,
 * in-app or from a separate terminal, while the folder is already open in iArtDev.
 *
 * `WatchService` only registers on directories, so this watches the parent and
 * filters events down to the one file. Reacting to a manifest change is safe by
 * construction: the renderer deletes+rewrites every image before writing
 * `manifest.json` last, in one `writeText` call — so a manifest-changed event always
 * means every image it references is already on disk.
 */
class SnapshotWatcher(
    private val manifestFile: File,
    private val onChanged: () -> Unit,
) {
    private var job: Job? = null
    private var watchService: WatchService? = null

    fun start(scope: CoroutineScope) {
        stop()
        val parent = manifestFile.parentFile ?: return
        val service = FileSystems.getDefault().newWatchService()
        watchService = service
        job = scope.launch(Dispatchers.IO) {
            try {
                parent.toPath().register(
                    service,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE,
                )
                while (isActive) {
                    val key = service.take()
                    val matched = key.pollEvents().any { event ->
                        (event.context() as? Path)?.toString() == manifestFile.name
                    }
                    key.reset()
                    if (matched) {
                        // Debounce so one logical write (which can surface as more than
                        // one modify event on some filesystems) triggers a single reload.
                        delay(DEBOUNCE_MS)
                        onChanged()
                    }
                }
            } catch (_: ClosedWatchServiceException) {
                // stop() closed the service to unblock the take() call above — normal shutdown.
            } finally {
                runCatching { service.close() }
            }
        }
    }

    /** Stops watching. Safe to call even if never started, or more than once. */
    fun stop() {
        job?.cancel()
        job = null
        // WatchService.take() blocks the underlying thread and ignores coroutine
        // cancellation; closing it is what actually unblocks that call.
        runCatching { watchService?.close() }
        watchService = null
    }

    private companion object {
        const val DEBOUNCE_MS = 400L
    }
}
