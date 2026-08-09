package iartdev.extension

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import artboard.model.PreviewFrame

/**
 * Contract for a plugin that customizes how iArtDev displays previews.
 *
 * Implementations are discovered at launch via [java.util.ServiceLoader] from jars
 * dropped into `~/.iartdev/extensions/`. Each jar must declare its implementation(s)
 * in `META-INF/services/iartdev.extension.IArtDevExtension`.
 */
interface IArtDevExtension {
    /** Stable, unique identifier (e.g. reverse-DNS style). Used as the enable/disable key. */
    val id: String

    /** Human-readable name shown in the Settings extensions list. */
    val displayName: String

    /** Called once after the extension is loaded and before it is asked to contribute anything. */
    fun onLoad() {}

    /**
     * Optional overlay drawn on top of a frame's chrome (e.g. a ruler, a device-frame
     * skin, an annotation layer). Return `null` to contribute nothing.
     */
    fun contributeCanvasOverlay(): (@Composable BoxScope.(frame: PreviewFrame) -> Unit)? = null
}
