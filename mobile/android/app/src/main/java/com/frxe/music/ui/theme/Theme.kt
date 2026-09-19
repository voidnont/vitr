package com.frxe.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FrxeColors = darkColorScheme(
    primary = Color(0xFFF22F5D),
    onPrimary = Color.White,
    secondary = Color(0xFFFF8AA5),
    background = Color(0xFF050507),
    onBackground = Color(0xFFF9F9FC),
    surface = Color(0xFF101116),
    onSurface = Color(0xFFF9F9FC),
    surfaceVariant = Color(0xFF1A1015),
    onSurfaceVariant = Color(0xFFE2C8CF),
    outline = Color(0xFF72404D)
)

@Composable
fun FrxeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FrxeColors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
