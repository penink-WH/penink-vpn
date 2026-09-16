package com.penink.vpn.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6C8CFF),
    onPrimary = Color.White,
    secondary = Color(0xFF9BE15D),
    background = Color(0xFF0E0E1A),
    surface = Color(0xFF16162B),
    surfaceVariant = Color(0xFF1F1F3A),
    onBackground = Color(0xFFEAEAFF),
    onSurface = Color(0xFFEAEAFF),
    onSurfaceVariant = Color(0xFFA6A6C8)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3D5AFE),
    onPrimary = Color.White,
    secondary = Color(0xFF4CAF50),
    background = Color(0xFFF4F5FF),
    surface = Color.White,
    surfaceVariant = Color(0xFFE8E9FF),
    onBackground = Color(0xFF1A1B2E),
    onSurface = Color(0xFF1A1B2E),
    onSurfaceVariant = Color(0xFF666680)
)

@Composable
fun PeninkVpnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}