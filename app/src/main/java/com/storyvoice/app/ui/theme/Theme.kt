package com.storyvoice.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StoryColors = lightColorScheme(
    primary = Color(0xFF2D5B4D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB2DCCA),
    secondary = Color(0xFF785826),
    secondaryContainer = Color(0xFFF3E2BF),
    background = Color(0xFFF8F5ED),
    surface = Color(0xFFFFFCF6),
    onBackground = Color(0xFF1D211E),
    onSurface = Color(0xFF1D211E),
    onSurfaceVariant = Color(0xFF69716C)
)

@Composable
fun StoryVoiceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StoryColors, content = content)
}
