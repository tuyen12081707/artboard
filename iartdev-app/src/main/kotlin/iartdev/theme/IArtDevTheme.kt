package iartdev.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import artboard.host.StudioTheme

/**
 * iArtDev's own chrome (onboarding, dialogs, empty/error states) — a warm amber
 * identity distinct from the board's cobalt "Drafting Studio," so the app's own
 * shell reads as a different layer from the Artboard content it hosts.
 *
 * Trimmed from [artboard.host.StudioColors]'s shape: only the tokens generic chrome
 * ever needs (no graph-paper grid, frame borders, or kind badges — those are
 * board-only concepts).
 */
@Immutable
data class IArtDevColors(
    val canvas: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val line: Color,
    val lineStrong: Color,
    val accent: Color,
    val accentInk: Color,
    val accentWash: Color,
    val onAccent: Color,
    val isDark: Boolean,
)

val LocalIArtDevColors = staticCompositionLocalOf<IArtDevColors> {
    error("IArtDevColors not provided — wrap chrome in IArtDevTheme { }")
}

/** Terse accessor, mirroring `artboard.host.Studio`. */
object IArtDev {
    val colors: IArtDevColors
        @Composable get() = LocalIArtDevColors.current
}

/** Amber-on-warm-paper — same neutral family as Artboard's own light palette. */
private val AmberLightColors = IArtDevColors(
    canvas = Color(0xFFE9E6E0),
    surface = Color(0xFFF6F5F1),
    surfaceRaised = Color(0xFFFCFBF7),
    ink = Color(0xFF262421),
    inkSoft = Color(0xFF5C574E),
    inkFaint = Color(0xFF9A9488),
    line = Color(0xFFD6D2C9),
    lineStrong = Color(0xFFA9A399),
    accent = Color(0xFFB5720B),
    accentInk = Color(0xFF7A4B06),
    accentWash = Color(0xFFF5E3BE),
    onAccent = Color(0xFFFFFFFF),
    isDark = false,
)

/** Amber-on-graphite — same neutral family as Artboard's own dark palette. */
private val AmberDarkColors = IArtDevColors(
    canvas = Color(0xFF1B1A18),
    surface = Color(0xFF262422),
    surfaceRaised = Color(0xFF302D2A),
    ink = Color(0xFFE9E6DF),
    inkSoft = Color(0xFFA39D92),
    inkFaint = Color(0xFF6E685E),
    line = Color(0xFF3A3733),
    lineStrong = Color(0xFF55514A),
    accent = Color(0xFFE8A94A),
    accentInk = Color(0xFFE8A94A),
    accentWash = Color(0xFF4A3A1E),
    onAccent = Color(0xFF241804),
    isDark = true,
)

/**
 * iArtDev's chrome theme. Nests the real [StudioTheme] so `Studio.type` (fonts:
 * Space Grotesk / IBM Plex Mono / FreeSans) resolves normally — only the color
 * tokens differ, reached through [IArtDev.colors] instead of `Studio.colors`.
 */
@Composable
fun IArtDevTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    StudioTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(
            LocalIArtDevColors provides if (darkTheme) AmberDarkColors else AmberLightColors,
            content = content,
        )
    }
}
