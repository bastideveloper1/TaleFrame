package com.r0ybt.taleframe.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TaleFrameColors = darkColorScheme(
    primary = Color(0xFFC8B6FF),
    secondary = Color(0xFFB6C5DD),
    background = Color(0xFF101014),
    surface = Color(0xFF18181F),
    surfaceVariant = Color(0xFF262630)
)

@Composable
fun TaleFrameTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TaleFrameColors, typography = Typography, content = content)
}
