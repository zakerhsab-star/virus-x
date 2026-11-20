package com.virusx.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val VirusXBackground: Color get() = VirusXThemeManager.currentScheme.background
val VirusXSurface: Color get() = VirusXThemeManager.currentScheme.surface
val VirusXSurfaceElevated: Color get() = VirusXThemeManager.currentScheme.surfaceVariant
val VirusXGlassBorder: Color get() = if (VirusXThemeManager.activeConfigState.isDarkMode) {
    VirusXThemeManager.activeConfigState.palette.borderGlowDark.copy(alpha = 0.40f)
} else {
    VirusXThemeManager.activeConfigState.palette.borderGlowLight.copy(alpha = 0.34f)
}
val VirusXGlassSurface: Color get() = VirusXThemeManager.currentScheme.surface
val VirusXPrimary: Color get() = VirusXThemeManager.currentScheme.primary
val VirusXPrimaryVariant: Color get() = VirusXThemeManager.currentScheme.secondary
val VirusXSecondaryWhisper: Color get() = VirusXThemeManager.currentScheme.primary
val VirusXWhisperSurface: Color get() = if (VirusXThemeManager.activeConfigState.isDarkMode) Color(0xFF0C2A36) else Color(0xFFE0F2FE)

val SentBubbleStart: Color get() = VirusXThemeManager.currentScheme.primary
val SentBubbleEnd: Color get() = VirusXThemeManager.currentScheme.secondary
val ReceivedBubbleColor: Color get() = VirusXThemeManager.currentScheme.surfaceVariant

val SentMessageGradient: Brush
    get() = Brush.linearGradient(
        colors = listOf(SentBubbleStart, SentBubbleEnd)
    )

val WhisperMessageGradient: Brush
    get() = Brush.linearGradient(
        colors = listOf(Color(0xFF0891B2), Color(0xFF06B6D4))
    )

val OnlineGreen = Color(0xFF10B981)
val WarningAmber = Color(0xFFF59E0B)
val DangerRed = Color(0xFFEF4444)

val TextPrimary: Color get() = VirusXThemeManager.currentScheme.onSurface
val TextSecondary: Color get() = VirusXThemeManager.currentScheme.onSurfaceVariant
val TextMuted: Color get() = VirusXThemeManager.currentScheme.onSurfaceVariant.copy(alpha = 0.7f)
