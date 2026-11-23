package io.roadie.carui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object CarColors {
    val Background = Color(0xFF090A0F)
    val BackgroundAlt = Color(0xFF111420)
    val Surface = Color(0xFF141722)
    val SurfaceElevated = Color(0xFF1C2030)
    val SurfaceBorder = Color(0xFF2B3044)
    val Accent = Color(0xFFFF0033)
    val AccentSoft = Color(0x29FF0033)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
}

private val DarkScheme = darkColorScheme(
    primary = CarColors.Accent,
    background = CarColors.Background,
    surface = CarColors.Surface,
    onPrimary = Color.White,
    onBackground = CarColors.TextPrimary,
    onSurface = CarColors.TextPrimary
)

@Composable
fun RoadieTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        content = content
    )
}
