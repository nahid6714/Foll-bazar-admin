package com.folbazar.admin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FolGreen = Color(0xFF00A651)
private val FolGreenSoft = Color(0xFFE9F9EF)
private val FolGreenDark = Color(0xFF008C45)
private val FolLime = Color(0xFFB7F000)

private val FolBazarColorScheme = lightColorScheme(
    primary = FolGreen,
    onPrimary = Color.White,
    primaryContainer = FolGreenSoft,
    onPrimaryContainer = Color(0xFF005A2A),
    secondary = FolGreenDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF5E6),
    onSecondaryContainer = Color(0xFF003D1D),
    tertiary = FolLime,
    onTertiary = Color(0xFF163000),
    background = Color(0xFFF7FBF8),
    surface = Color.White
)

@Composable
fun FolBazarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FolBazarColorScheme,
        content = content
    )
}
