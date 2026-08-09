package iartdev

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import artboard.host.ArtboardApp
import artboard.host.LocalStudioColors
import artboard.host.Studio
import artboard.host.StudioTheme
import artboard.model.PreviewFrame
import artboard.registry.ArtboardRegistry
import iartdev.prefs.RecentFolder
import iartdev.prefs.RecentFolders
import iartdev.snapshot.SnapshotImageStore
import iartdev.snapshot.SnapshotManifest
import iartdev.snapshot.SnapshotTile
import iartdev.snapshot.SnapshotWatcher
import iartdev.snapshot.parseSnapshotManifest
import iartdev.ui.Onboarding
import iartdev.ui.SnapshotRunnerDialog
import java.io.File
import java.util.prefs.Preferences
import javax.swing.JFileChooser
import kotlinx.coroutines.launch

private const val MANIFEST_FILE_NAME = "manifest.json"

fun main(args: Array<String>) = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "iArtDev",
        state = WindowState(width = 1360.dp, height = 860.dp),
    ) {
        IArtDevApp(initialFolder = args.getOrNull(0))
    }
}

private sealed interface LoadState {
    data object Empty : LoadState
    data class Loading(val folder: File) : LoadState
    data class Loaded(
        val folder: File,
        val manifest: SnapshotManifest,
        val store: SnapshotImageStore,
        val registry: ArtboardRegistry,
    ) : LoadState

    data class Failed(val folder: File, val reason: String) : LoadState
}

@Composable
private fun IArtDevApp(initialFolder: String? = null) {
    val prefs = remember { Preferences.userRoot().node("dev.iartdev") }
    var state by remember { mutableStateOf<LoadState>(LoadState.Empty) }
    var recent by remember { mutableStateOf<List<RecentFolder>>(emptyList()) }
    var showRunner by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun open(folder: File) {
        val manifestFile = File(folder, MANIFEST_FILE_NAME)
        if (!manifestFile.isFile) {
            state = LoadState.Failed(
                folder,
                "No $MANIFEST_FILE_NAME in this folder. Run artboardSnapshot or " +
                    "artboardExport on your project first, then open its output folder.",
            )
            return
        }
        state = LoadState.Loading(folder)
        scope.launch {
            runCatching { parseSnapshotManifest(manifestFile.readText()) }
                .onSuccess { manifest ->
                    val store = SnapshotImageStore(folder)
                    val registry = manifest.toRegistry(store)
                    state = LoadState.Loaded(folder, manifest, store, registry)
                    recent = RecentFolders.recordOpened(prefs, folder)
                    store.loadAll(manifest)
                }
                .onFailure { failure ->
                    state = LoadState.Failed(folder, failure.message ?: "Could not read $MANIFEST_FILE_NAME")
                }
        }
    }

    // Reopen the last project on launch so double-clicking the app is enough after day one.
    // A launch argument (e.g. an "Open with iArtDev" association) overrides this.
    remember {
        RecentFolders.migrateLegacyIfNeeded(prefs)
        recent = RecentFolders.load(prefs)
        val path = initialFolder ?: recent.firstOrNull()?.path
        path?.let { File(it) }
            ?.takeIf { it.isDirectory }
            ?.let(::open)
    }

    // Auto-reload if the open folder's manifest is rewritten (in-app runner or a
    // terminal re-run of artboardSnapshot) — no restart needed.
    val loadedFolder = (state as? LoadState.Loaded)?.folder
    if (loadedFolder != null) {
        DisposableEffect(loadedFolder) {
            val watcher = SnapshotWatcher(File(loadedFolder, MANIFEST_FILE_NAME)) { open(loadedFolder) }
            watcher.start(scope)
            onDispose { watcher.stop() }
        }
    }

    when (val current = state) {
        is LoadState.Loaded ->
            Box(modifier = Modifier.fillMaxSize()) {
                ArtboardApp(
                    registry = current.registry,
                    title = current.manifest.title,
                    initialDarkTheme = isSystemInDarkTheme(),
                    modifier = Modifier.fillMaxSize(),
                )
                ChangeFolderAction(
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    onClick = { pickFolder()?.let(::open) },
                )
            }

        is LoadState.Loading ->
            Onboarding(
                isLoading = true,
                loadingLabel = "Loading ${current.folder.name}…",
                errorMessage = null,
                recent = recent,
                onOpenFolder = { pickFolder()?.let(::open) },
                onOpenRecent = ::open,
                onRemoveRecent = { path -> recent = RecentFolders.remove(prefs, path) },
                onRunSnapshot = { showRunner = true },
                onFolderDropped = ::open,
            )

        is LoadState.Failed ->
            Onboarding(
                isLoading = false,
                loadingLabel = "",
                errorMessage = current.reason,
                recent = recent,
                onOpenFolder = { pickFolder()?.let(::open) },
                onOpenRecent = ::open,
                onRemoveRecent = { path -> recent = RecentFolders.remove(prefs, path) },
                onRunSnapshot = { showRunner = true },
                onFolderDropped = ::open,
            )

        LoadState.Empty ->
            Onboarding(
                isLoading = false,
                loadingLabel = "",
                errorMessage = null,
                recent = recent,
                onOpenFolder = { pickFolder()?.let(::open) },
                onOpenRecent = ::open,
                onRemoveRecent = { path -> recent = RecentFolders.remove(prefs, path) },
                onRunSnapshot = { showRunner = true },
                onFolderDropped = ::open,
            )
    }

    if (showRunner) {
        SnapshotRunnerDialog(
            onDismiss = { showRunner = false },
            onSnapshotReady = { folder ->
                showRunner = false
                open(folder)
            },
        )
    }
}

/** Registers each manifest frame as a [PreviewFrame] whose body draws its snapshot tile. */
private fun SnapshotManifest.toRegistry(store: SnapshotImageStore): ArtboardRegistry {
    val manifest = this
    val previewFrames = frames.map { frame ->
        PreviewFrame(
            id = frame.id,
            name = frame.name,
            group = frame.group,
            kind = frame.kind,
            widthDp = frame.widthDp,
            heightDp = frame.heightDp,
            sourceFqName = frame.sourceFqName,
            content = { SnapshotTile(frame = frame, failureReason = manifest.failureFor(frame.id), store = store) },
        )
    }
    return object : ArtboardRegistry {
        override val frames: List<PreviewFrame> = previewFrames
    }
}

/** Native folder picker; returns null if the user cancels. */
private fun pickFolder(): File? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Open Artboard snapshot folder"
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}

/**
 * Overlaid on top of [ArtboardApp], so it needs its own [StudioTheme] scope —
 * it is a sibling of the board's chrome, not a descendant of it. Deliberately keeps
 * Artboard's cobalt palette (not iArtDev's amber) since it's board-adjacent chrome.
 */
@Composable
private fun ChangeFolderAction(modifier: Modifier = Modifier, onClick: () -> Unit) {
    StudioTheme(darkTheme = isSystemInDarkTheme()) {
        val colors = LocalStudioColors.current
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceRaised.copy(alpha = if (hovered) 1f else 0.85f))
                .border(1.dp, colors.line, RoundedCornerShape(8.dp))
                .hoverable(interaction)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            BasicText(text = "Open different folder…", style = Studio.type.label.copy(color = colors.ink))
        }
    }
}
