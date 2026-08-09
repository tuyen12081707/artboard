@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package iartdev.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import artboard.host.Studio
import iartdev.prefs.RecentFolder
import iartdev.theme.IArtDev
import iartdev.theme.IArtDevTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ERROR_COLOR = Color(0xFFB3261E)

/**
 * iArtDev's first-run/empty-state screen: explains what to do, offers the two ways
 * to get a gallery open (run Gradle in-app, or point at an already-generated
 * snapshot folder), lists recent projects, and accepts a dropped folder anywhere
 * on the surface.
 */
@Composable
fun Onboarding(
    isLoading: Boolean,
    loadingLabel: String,
    errorMessage: String?,
    recent: List<RecentFolder>,
    onOpenFolder: () -> Unit,
    onOpenRecent: (File) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onRunSnapshot: () -> Unit,
    onFolderDropped: (File) -> Unit,
) {
    IArtDevTheme(darkTheme = isSystemInDarkTheme()) {
        val colors = IArtDev.colors
        var isDragOver by remember { mutableStateOf(false) }
        val dropTarget = remember {
            object : DragAndDropTarget {
                override fun onEntered(event: DragAndDropEvent) {
                    isDragOver = true
                }

                override fun onExited(event: DragAndDropEvent) {
                    isDragOver = false
                }

                override fun onEnded(event: DragAndDropEvent) {
                    isDragOver = false
                }

                override fun onDrop(event: DragAndDropEvent): Boolean {
                    isDragOver = false
                    val folder = (event.dragData() as? DragData.FilesList)
                        ?.readFiles()
                        ?.firstOrNull()
                        ?.let(::File)
                        ?.let { if (it.isDirectory) it else it.parentFile }
                    return if (folder != null) {
                        onFolderDropped(folder)
                        true
                    } else {
                        false
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.canvas)
                .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropTarget)
                .then(
                    if (isDragOver) {
                        Modifier.border(2.dp, colors.accent, RoundedCornerShape(0.dp))
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(text = "iArtDev", style = Studio.type.zoneHeader.copy(color = colors.ink))
                Spacer(Modifier.height(6.dp))
                BasicText(
                    text = "Standalone viewer for Artboard preview galleries.",
                    style = Studio.type.body.copy(color = colors.inkSoft, textAlign = TextAlign.Center),
                )

                if (errorMessage != null) {
                    Spacer(Modifier.height(16.dp))
                    BasicText(
                        text = errorMessage,
                        style = Studio.type.body.copy(color = ERROR_COLOR, textAlign = TextAlign.Center),
                    )
                }

                if (isLoading) {
                    Spacer(Modifier.height(20.dp))
                    BasicText(text = loadingLabel, style = Studio.type.mono.copy(color = colors.inkSoft))
                    return@Column
                }

                Spacer(Modifier.height(28.dp))
                OnboardingStep(1, "Point iArtDev at your project", "It finds your Compose module and offers to apply the Artboard plugin for you — no manual editing.")
                OnboardingStep(2, "Run a snapshot", "iArtDev runs it for you — or use a terminal: ./gradlew :module:artboardSnapshot")
                OnboardingStep(3, "Browse the result", "Opens automatically, and auto-refreshes whenever you re-run a snapshot.")

                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(label = "Run Snapshot from Project…", onClick = onRunSnapshot)
                    SecondaryButton(label = "Open Snapshot Folder…", onClick = onOpenFolder)
                }

                if (recent.isNotEmpty()) {
                    Spacer(Modifier.height(28.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BasicText(
                            text = "RECENT",
                            style = Studio.type.badge.copy(color = colors.inkFaint),
                        )
                        Spacer(Modifier.height(8.dp))
                        recent.forEach { entry ->
                            RecentRow(entry = entry, onOpen = onOpenRecent, onRemove = onRemoveRecent)
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                BasicText(
                    text = "or drag a snapshot folder anywhere onto this window",
                    style = Studio.type.label.copy(color = colors.inkFaint, textAlign = TextAlign.Center),
                )
            }
        }
    }
}

@Composable
private fun OnboardingStep(number: Int, title: String, description: String) {
    val colors = IArtDev.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(colors.accentWash),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(text = number.toString(), style = Studio.type.mono.copy(color = colors.accentInk))
        }
        Column(horizontalAlignment = Alignment.Start) {
            BasicText(text = title, style = Studio.type.label.copy(color = colors.ink))
            BasicText(text = description, style = Studio.type.body.copy(color = colors.inkSoft))
        }
    }
}

@Composable
private fun RecentRow(entry: RecentFolder, onOpen: (File) -> Unit, onRemove: (String) -> Unit) {
    val colors = IArtDev.colors
    val folder = File(entry.path)
    val missing = !folder.isDirectory
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (hovered) colors.surfaceRaised else Color.Transparent)
            .border(1.dp, colors.line, RoundedCornerShape(8.dp))
            .hoverable(interaction)
            .pointerHoverIcon(if (missing) PointerIcon.Default else PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, enabled = !missing) { onOpen(folder) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(text = folder.name, style = Studio.type.label.copy(color = colors.ink))
            BasicText(
                text = if (missing) "Missing — ${folder.parent}" else timeAgo(entry.openedAtEpochMillis),
                style = Studio.type.mono.copy(color = if (missing) ERROR_COLOR else colors.inkFaint),
            )
        }
        BasicText(
            text = "Remove",
            style = Studio.type.label.copy(color = colors.inkFaint),
            modifier = Modifier
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    onRemove(entry.path)
                }
                .padding(4.dp),
        )
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
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
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    val colors = IArtDev.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, colors.line, RoundedCornerShape(8.dp))
            .background(if (hovered) colors.surfaceRaised else Color.Transparent)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        BasicText(text = label, style = Studio.type.label.copy(color = colors.ink))
    }
}

private val timeAgoFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

private fun timeAgo(epochMillis: Long): String {
    val minutes = (System.currentTimeMillis() - epochMillis) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> timeAgoFormat.format(Date(epochMillis))
    }
}
