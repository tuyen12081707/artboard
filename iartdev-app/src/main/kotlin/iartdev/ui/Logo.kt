package iartdev.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import iartdev.theme.IArtDev

/**
 * In-app vector wordmark — a drawn monogram, not a designer-produced binary asset (see
 * docs/REDESIGN_PLAN.md, Decision D3). Theme-aware: uses the amber accent tokens so it
 * matches whichever of [iartdev.theme.IArtDevTheme]'s two palettes is active.
 *
 * Draws a simple angled "A" mark inside a rounded chip — legible at the small sizes a
 * sidebar header needs, unlike a typographic wordmark set in a tiny font.
 */
@Composable
fun LogoMark(modifier: Modifier = Modifier, sizeDp: Dp = 28.dp) {
    val colors = IArtDev.colors
    Canvas(
        modifier
            .size(sizeDp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.accentWash),
    ) {
        val strokeWidth = size.minDimension * 0.11f
        val inset = size.minDimension * 0.26f
        val top = Offset(size.width / 2f, inset)
        val bottomLeft = Offset(inset, size.height - inset)
        val bottomRight = Offset(size.width - inset, size.height - inset)
        val midLeft = Offset(
            bottomLeft.x + (top.x - bottomLeft.x) * 0.45f,
            bottomLeft.y + (top.y - bottomLeft.y) * 0.45f,
        )
        val midRight = Offset(
            bottomRight.x + (top.x - bottomRight.x) * 0.45f,
            bottomRight.y + (top.y - bottomRight.y) * 0.45f,
        )
        drawLine(colors.accentInk, top, bottomLeft, strokeWidth, cap = Stroke.DefaultCap)
        drawLine(colors.accentInk, top, bottomRight, strokeWidth, cap = Stroke.DefaultCap)
        drawLine(colors.accentInk, midLeft, midRight, strokeWidth, cap = Stroke.DefaultCap)
    }
}
