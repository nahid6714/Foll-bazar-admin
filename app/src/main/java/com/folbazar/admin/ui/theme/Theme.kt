package com.folbazar.admin.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

private val FolGreen = Color(0xFF00A651)
private val FolGreenSoft = Color(0xFFE9F9EF)
private val FolGreenDark = Color(0xFF008C45)
private val FolLime = Color(0xFFB7F000)

private val LightScheme = lightColorScheme(
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

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF4DDB8B),
    onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0xFF005A2A),
    onPrimaryContainer = Color(0xFFB5F5C9),
    secondary = Color(0xFF7AE6A5),
    onSecondary = Color(0xFF00391C),
    background = Color(0xFF0D1510),
    surface = Color(0xFF111A14),
    surfaceContainerLow = Color(0xFF18221C)
)

object ThemePrefs {
    enum class Mode { SYSTEM, LIGHT, DARK }
    private const val PREF = "fol_bazar_theme"
    private const val KEY = "mode"
    private var prefs: android.content.SharedPreferences? = null

    var mode: Mode by mutableStateOf(Mode.SYSTEM)
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        mode = runCatching { Mode.valueOf(prefs?.getString(KEY, Mode.SYSTEM.name) ?: Mode.SYSTEM.name) }.getOrDefault(Mode.SYSTEM)
    }

    fun updateMode(value: Mode) {
        mode = value
        prefs?.edit()?.putString(KEY, value.name)?.apply()
    }
}

@Composable
fun FolBazarTheme(content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemePrefs.Mode.DARK -> true
        ThemePrefs.Mode.LIGHT -> false
        ThemePrefs.Mode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        content = content
    )
}
