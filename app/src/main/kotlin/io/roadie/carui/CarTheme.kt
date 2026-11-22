package io.roadie.carui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object CarColors {
    val Background = Color(0xFF0F0F0F)
    val Surface = Color(0xFF1A1A1A)
    val SurfaceElevated = Color(0xFF242424)
    val Accent = Color(0xFFFF0033)
    val TextPrimary = Color.White
    val TextSecondary = Color(0xFFAAAAAA)
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
