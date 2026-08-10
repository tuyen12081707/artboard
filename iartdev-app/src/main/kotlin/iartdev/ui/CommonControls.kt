package iartdev.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import artboard.host.Studio
import iartdev.theme.IArtDev

/**
 * Shared chrome controls for the new nav shell (sidebar, bottom bar, Tool Paths, Help).
 * Mirrors the look of [Onboarding]'s private `PrimaryButton`/`SecondaryButton` — kept as
 * separate public copies rather than exporting those, so the working onboarding screen
 * is untouched by this pass.
 */
@Composable
fun PrimaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = IArtDev.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = modifier
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
fun SecondaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = IArtDev.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = modifier
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

@Composable
fun TextAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = IArtDev.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        BasicText(text = label, style = Studio.type.label.copy(color = colors.accentInk))
    }
}
