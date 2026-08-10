package iartdev

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import artboard.host.ArtboardApp
import artboard.host.Studio
import artboard.model.PreviewFrame
import artboard.registry.ArtboardRegistry
import iartdev.gradlerunner.GradleProjectScanner
import iartdev.gradlerunner.GradleSnapshotProcess
import iartdev.gradlerunner.KeepAwake
import iartdev.gradlerunner.RunEvent
import iartdev.prefs.RecentFolder
import iartdev.prefs.RecentFolders
import iartdev.prefs.RunConfig
import iartdev.prefs.RunConfigStore
import iartdev.prefs.Session
import iartdev.snapshot.SnapshotImageStore
import iartdev.snapshot.SnapshotManifest
import iartdev.snapshot.SnapshotTile
import iartdev.snapshot.SnapshotWatcher
import iartdev.snapshot.parseSnapshotManifest
import iartdev.theme.IArtDevTheme
import iartdev.ui.BottomBar
import iartdev.ui.HelpScreen
import iartdev.ui.Onboarding
import iartdev.ui.Screen
import iartdev.ui.Sidebar
import iartdev.ui.SnapshotRunnerDialog
import iartdev.ui.ToolPathsScreen
import java.io.File
import java.util.prefs.Preferences
import javax.swing.JFileChooser
import kotlinx.coroutines.launch

private const val MANIFEST_FILE_NAME = "manifest.json"
private val SYNC_ERROR_COLOR = Color(0xFFB3261E)

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
    var selectedScreen by remember { mutableStateOf(Screen.Gallery) }
    var displayName by remember { mutableStateOf<String?>(null) }
    var lastRunConfig by remember { mutableStateOf<RunConfig?>(null) }
    var syncing by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf<String?>(null) }
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

    /**
     * Re-runs the last successful (project root, Gradle path) without walking back
     * through [SnapshotRunnerDialog]'s picker. Success is picked up implicitly by the
     * [SnapshotWatcher] already active on the loaded folder below; only failure needs
     * surfacing here.
     */
    fun sync() {
        val config = lastRunConfig ?: return
        val root = File(config.projectRootPath)
        val wrapper = GradleProjectScanner.findWrapper(root)
        if (wrapper == null) {
            syncError = "No gradlew found at ${root.path} anymore — use Run Snapshot… to pick a new root."
            return
        }
        syncing = true
        syncError = null
        val keepAwake = KeepAwake()
        keepAwake.start()
        scope.launch {
            GradleSnapshotProcess().run(root, wrapper, config.gradlePath).collect { event ->
                when (event) {
                    is RunEvent.Line -> Unit
                    is RunEvent.Finished -> {
                        keepAwake.stop()
                        syncing = false
                        if (event.exitCode != 0) {
                            syncError = "Sync failed — Gradle exited with code ${event.exitCode}."
                        }
                    }
                    is RunEvent.Failed -> {
                        keepAwake.stop()
                        syncing = false
                        syncError = event.error.message ?: "Could not start Gradle."
                    }
                }
            }
        }
    }

    // Reopen the last project on launch so double-clicking the app is enough after day one.
    // A launch argument (e.g. an "Open with iArtDev" association) overrides this.
    remember {
        RecentFolders.migrateLegacyIfNeeded(prefs)
        recent = RecentFolders.load(prefs)
        displayName = Session.loadDisplayName(prefs)
        lastRunConfig = RunConfigStore.load(prefs)
        val path = initialFolder ?: recent.firstOrNull()?.path
        path?.let { File(it) }
            ?.takeIf { it.isDirectory }
            ?.let(::open)
    }

    // Auto-reload if the open folder's manifest is rewritten (in-app runner, Sync, or a
    // terminal re-run of artboardSnapshot) — no restart needed.
    val loadedFolder = (state as? LoadState.Loaded)?.folder
    if (loadedFolder != null) {
        DisposableEffect(loadedFolder) {
            val watcher = SnapshotWatcher(File(loadedFolder, MANIFEST_FILE_NAME)) { open(loadedFolder) }
            watcher.start(scope)
            onDispose { watcher.stop() }
        }
    }

    IArtDevTheme(darkTheme = isSystemInDarkTheme()) {
        Row(modifier = Modifier.fillMaxSize()) {
            Sidebar(
                selected = selectedScreen,
                onSelect = { selectedScreen = it },
                onRunSnapshot = { showRunner = true },
                displayName = displayName,
                onSignIn = { name ->
                    Session.signIn(prefs, name)
                    displayName = name
                },
                onSignOut = {
                    Session.signOut(prefs)
                    displayName = null
                },
            )
            Column(modifier = Modifier.fillMaxSize().weight(1f)) {
                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    when (selectedScreen) {
                        Screen.Gallery -> GalleryContent(
                            state = state,
                            recent = recent,
                            onOpenFolder = { pickFolder()?.let(::open) },
                            onOpenRecent = ::open,
                            onRemoveRecent = { path -> recent = RecentFolders.remove(prefs, path) },
                            onRunSnapshot = { showRunner = true },
                            onFolderDropped = ::open,
                        )

                        Screen.ToolPaths -> ToolPathsScreen(modifier = Modifier.fillMaxSize())
                        Screen.Help -> HelpScreen(modifier = Modifier.fillMaxSize())
                    }
                }
                syncError?.let { message ->
                    BasicText(
                        text = message,
                        style = Studio.type.label.copy(color = SYNC_ERROR_COLOR),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                BottomBar(
                    onChangeFolder = { pickFolder()?.let(::open) },
                    onRunSnapshot = { showRunner = true },
                    onSync = ::sync,
                    syncEnabled = lastRunConfig != null,
                    syncing = syncing,
                )
            }
        }
    }

    if (showRunner) {
        SnapshotRunnerDialog(
            onDismiss = { showRunner = false },
            onSnapshotReady = { folder ->
                showRunner = false
                lastRunConfig = RunConfigStore.load(prefs)
                open(folder)
            },
        )
    }
}

@Composable
private fun GalleryContent(
    state: LoadState,
    recent: List<RecentFolder>,
    onOpenFolder: () -> Unit,
    onOpenRecent: (File) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onRunSnapshot: () -> Unit,
    onFolderDropped: (File) -> Unit,
) {
    when (state) {
        is LoadState.Loaded ->
            ArtboardApp(
                registry = state.registry,
                title = state.manifest.title,
                initialDarkTheme = isSystemInDarkTheme(),
                modifier = Modifier.fillMaxSize(),
            )

        is LoadState.Loading ->
            Onboarding(
                isLoading = true,
                loadingLabel = "Loading ${state.folder.name}…",
                errorMessage = null,
                recent = recent,
                onOpenFolder = onOpenFolder,
                onOpenRecent = onOpenRecent,
                onRemoveRecent = onRemoveRecent,
                onRunSnapshot = onRunSnapshot,
                onFolderDropped = onFolderDropped,
            )

        is LoadState.Failed ->
            Onboarding(
                isLoading = false,
                loadingLabel = "",
                errorMessage = state.reason,
                recent = recent,
                onOpenFolder = onOpenFolder,
                onOpenRecent = onOpenRecent,
                onRemoveRecent = onRemoveRecent,
                onRunSnapshot = onRunSnapshot,
                onFolderDropped = onFolderDropped,
            )

        LoadState.Empty ->
            Onboarding(
                isLoading = false,
                loadingLabel = "",
                errorMessage = null,
                recent = recent,
                onOpenFolder = onOpenFolder,
                onOpenRecent = onOpenRecent,
                onRemoveRecent = onRemoveRecent,
                onRunSnapshot = onRunSnapshot,
                onFolderDropped = onFolderDropped,
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
