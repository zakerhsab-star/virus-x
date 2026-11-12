package com.virusx.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val VirusXBackground = Color(0xFF0B0E14)
val VirusXSurface = Color(0xFF151A24)
val VirusXSurfaceElevated = Color(0xFF1C2331)
val VirusXGlassBorder = Color(0x33FFFFFF)
val VirusXGlassSurface = Color(0xCC151A24)

val VirusXPrimary = Color(0xFF6366F1)
val VirusXPrimaryVariant = Color(0xFF8B5CF6)
val VirusXSecondaryWhisper = Color(0xFF06B6D4)
val VirusXWhisperSurface = Color(0xFF0C2A36)

val SentBubbleStart = Color(0xFF6366F1)
val SentBubbleEnd = Color(0xFF8B5CF6)
val ReceivedBubbleColor = Color(0xFF1E2532)

val SentMessageGradient = Brush.linearGradient(
    colors = listOf(SentBubbleStart, SentBubbleEnd)
)

val WhisperMessageGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF0891B2), Color(0xFF06B6D4))
)

val OnlineGreen = Color(0xFF10B981)
val WarningAmber = Color(0xFFF59E0B)
val DangerRed = Color(0xFFEF4444)

val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)