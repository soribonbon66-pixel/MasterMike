package com.michel.masteringpro.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Michel brand colors
val MichelPrimary = Color(0xFF6C63FF)
val MichelSecondary = Color(0xFF03DAC5)
val MichelTertiary = Color(0xFFFF6B6B)
val MichelSurface = Color(0xFF1A1A2E)
val MichelSurfaceVariant = Color(0xFF16213E)
val MichelBackground = Color(0xFF0F0F23)
val MichelOnSurface = Color(0xFFE8E8E8)
val MichelError = Color(0xFFFF4444)
val MichelWarning = Color(0xFFFFAA00)
val MichelSuccess = Color(0xFF4CAF50)

private val DarkColorScheme = darkColorScheme(
    primary = MichelPrimary,
    secondary = MichelSecondary,
    tertiary = MichelTertiary,
    surface = MichelSurface,
    surfaceVariant = MichelSurfaceVariant,
    background = MichelBackground,
    onSurface = MichelOnSurface,
    onBackground = MichelOnSurface,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    error = MichelError,
)

@Composable
fun MichelTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = MichelBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MichelTypography,
        content = content
    )
}
