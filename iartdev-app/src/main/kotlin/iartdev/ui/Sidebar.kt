package iartdev.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import artboard.host.Studio
import iartdev.theme.IArtDev

/**
 * Persistent left sidebar: profile block, then one row per [Screen] plus a "Run
 * Snapshot" action row that opens the existing modal wizard rather than navigating.
 * See docs/REDESIGN_PLAN.md §3 for the shell diagram this implements.
 */
@Composable
fun Sidebar(
    selected: Screen,
    onSelect: (Screen) -> Unit,
    onRunSnapshot: () -> Unit,
    displayName: String?,
    onSignIn: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IArtDev.colors
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(216.dp)
            .background(colors.surface)
            .padding(vertical = 16.dp, horizontal = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoMark(sizeDp = 26.dp)
            Spacer(Modifier.width(8.dp))
            BasicText(text = "iArtDev", style = Studio.type.label.copy(color = colors.ink))
        }

        Spacer(Modifier.height(18.dp))
        ProfileBlock(displayName = displayName, onSignIn = onSignIn, onSignOut = onSignOut)

        Spacer(Modifier.height(20.dp))
        BasicText(text = "WORKSPACE", style = Studio.type.badge.copy(color = colors.inkFaint))
        Spacer(Modifier.height(8.dp))

        SidebarRow(label = "Run Snapshot…", selected = false, onClick = onRunSnapshot)
        Screen.entries.forEach { screen ->
            SidebarRow(label = screen.label, selected = screen == selected, onClick = { onSelect(screen) })
        }
    }
}

@Composable
private fun SidebarRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = IArtDev.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    selected -> colors.accentWash
                    hovered -> colors.surfaceRaised
                    else -> androidx.compose.ui.graphics.Color.Transparent
                },
            )
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        BasicText(
            text = label,
            style = Studio.type.body.copy(color = if (selected) colors.accentInk else colors.ink),
        )
    }
}

@Composable
private fun ProfileBlock(displayName: String?, onSignIn: (String) -> Unit, onSignOut: () -> Unit) {
    val colors = IArtDev.colors
    if (displayName != null) {
        Column {
            BasicText(text = "Hi, $displayName", style = Studio.type.body.copy(color = colors.ink))
            Spacer(Modifier.height(4.dp))
            TextAction(label = "Log out", onClick = onSignOut)
        }
        return
    }

    var draft by remember { mutableStateOf("") }
    Column {
        BasicText(text = "Not signed in", style = Studio.type.label.copy(color = colors.inkFaint))
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = Studio.type.body.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .widthIn(min = 90.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, colors.line, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Spacer(Modifier.width(6.dp))
            TextAction(
                label = "Save",
                onClick = {
                    if (draft.isNotBlank()) {
                        onSignIn(draft)
                        draft = ""
                    }
                },
            )
        }
    }
}
