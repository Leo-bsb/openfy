package com.openfy.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val OpenfyInk = Color(0xFF060909)
val OpenfyPanel = Color(0xFF0B1312)
val OpenfyPanelHigh = Color(0xFF13201D)
val OpenfyLine = Color(0xFF1D2C28)
val OpenfyTextHigh = Color(0xFFF1F7F4)
val OpenfyTextLow = Color(0xFF8CA6A0)
val OpenfyAccent = Color(0xFF2FBFA0)
val OpenfyAccent2 = Color(0xFF19D3E6)

private val OpenfyScheme = darkColorScheme(
    primary = OpenfyAccent,
    onPrimary = OpenfyInk,
    secondary = OpenfyAccent2,
    onSecondary = OpenfyInk,
    background = OpenfyInk,
    onBackground = OpenfyTextHigh,
    surface = OpenfyPanel,
    onSurface = OpenfyTextHigh,
    surfaceVariant = OpenfyPanelHigh,
    onSurfaceVariant = OpenfyTextLow,
    outline = OpenfyLine,
    error = Color(0xFFFFB4AB),
    onError = OpenfyInk,
)

@Composable
fun OpenfyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OpenfyScheme,
        typography = Typography,
        content = content,
    )
}
