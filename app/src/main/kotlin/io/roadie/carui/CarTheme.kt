package io.roadie.carui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Official YouTube Dark color tokens adapted for automotive displays. */
object CarColors {
    val Background = Color(0xFF0F0F0F)
    val BackgroundAlt = Color(0xFF0F0F0F)
    val Surface = Color(0xFF212121)
    val SurfaceElevated = Color(0xFF272727)
    val SurfaceBorder = Color(0xFF303030)
    val Accent = Color(0xFFFF0000)
    val TextPrimary = Color(0xFFF1F1F1)
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
