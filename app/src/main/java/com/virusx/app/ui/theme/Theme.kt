package com.virusx.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val VirusXDarkColorScheme = darkColorScheme(
    primary = VirusXPrimary,
    onPrimary = TextPrimary,
    secondary = VirusXSecondaryWhisper,
    onSecondary = VirusXBackground,
    tertiary = VirusXPrimaryVariant,
    background = VirusXBackground,
    onBackground = TextPrimary,
    surface = VirusXSurface,
    onSurface = TextPrimary,
    surfaceVariant = VirusXSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    error = DangerRed,
    onError = TextPrimary
)

@Composable
fun VirusXTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = VirusXBackground.toArgb()
                window.navigationBarColor = VirusXBackground.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = VirusXDarkColorScheme,
        typography = VirusXTypography,
        content = content
    )
}