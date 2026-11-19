package com.virusx.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun VirusXTheme(
    content: @Composable () -> Unit
) {
    val themeCtx = LocalContext.current
    LaunchedEffect(Unit) { VirusXThemeManager.init(themeCtx) }
    val themeCfg by VirusXThemeManager.config.collectAsState()
    val dynamicScheme = VirusXThemeManager.buildColorScheme(themeCfg)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = dynamicScheme.background.toArgb()
                window.navigationBarColor = dynamicScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !themeCfg.isDarkMode
                    isAppearanceLightNavigationBars = !themeCfg.isDarkMode
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = dynamicScheme,
        typography = VirusXTypography,
        content = content
    )
}
