package iartdev.snapshot

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

/**
 * Decoded snapshot images, keyed by manifest-relative path, loaded from [root] on disk.
 *
 * JVM port of the wasmJs `SnapshotImageStore`: images are owned here rather than by
 * each tile so camera movement (which recomposes frame bodies) cannot interrupt a
 * decode in flight.
 */
internal class SnapshotImageStore(private val root: File) {
    val images = mutableStateMapOf<String, ImageBitmap>()
    val errors = mutableStateMapOf<String, String>()

    /** Decodes every image in [manifest] off the UI thread, publishing each as it lands. */
    suspend fun loadAll(manifest: SnapshotManifest) {
        val paths = manifest.frames
            .flatMap { frame -> frame.images.values.flatMap { byTheme -> byTheme.values } }
            .distinct()

        for (path in paths) {
            if (images.containsKey(path) || errors.containsKey(path)) continue
            runCatching { withContext(Dispatchers.IO) { decode(path) } }
                .fold(
                    onSuccess = { images[path] = it },
                    onFailure = { errors[path] = it.message ?: "Failed to load $path" },
                )
        }
    }

    private fun decode(path: String): ImageBitmap {
        val file = File(root, path)
        if (!file.isFile) error("Missing image file: ${file.path}")
        return SkiaImage.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
    }
}
