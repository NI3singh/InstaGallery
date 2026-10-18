package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = IgBlue,
    onPrimary = IgTextPrimary,
    secondary = IgTextSecondary,
    onSecondary = IgBlack,
    background = IgBlack,
    onBackground = IgTextPrimary,
    surface = IgBlack,
    onSurface = IgTextPrimary,
    surfaceVariant = IgElevated,
    onSurfaceVariant = IgTextSecondary,
    outline = IgBorderNav,
    outlineVariant = IgBorder
)

@Composable
fun InstaGalleryTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = IgBlack.toArgb()
            window.navigationBarColor = IgBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
